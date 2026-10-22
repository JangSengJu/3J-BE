package com.jjj.jjj.domain.ai;

import com.jjj.jjj.domain.ai.client.GeminiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiClient geminiClient;

    private String uniqueId() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void signUp(String id) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isCreated());
    }

    private String accessToken(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
    }

    private String auth(String id) throws Exception {
        signUp(id);
        return accessToken(id);
    }

    @Test
    @DisplayName("POST /api/ai/recommend는 인증 필요")
    void recommendRequiresAuth() throws Exception {
        String payload = body(Map.of("input", Map.of("resources", List.of())));
        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/ai/recommend는 Gemini 결과를 정규화한다")
    void recommendNormalizesFromGemini() throws Exception {
        String token = auth(uniqueId());
        Mockito.when(geminiClient.isAvailable()).thenReturn(true);
        Mockito.when(geminiClient.generateJson(anyString(), anyString())).thenReturn(
                "{\"recommendations\":[{\"title\":\"테스트\",\"target\":\"vm-a\",\"savings\":50,\"grounds\":[\"과부하 낮음\"],\"action\":\"DOWNSIZING\",\"kind\":\"COST\"}]}"
        );

        String payload = body(Map.of(
                "input", Map.of(
                        "limit", 1000,
                        "total", 500,
                        "purpose", "테스트",
                        "resources", List.of(Map.of(
                                "name", "vm-a",
                                "type", "vm",
                                "cost", 100,
                                "usage", "10%",
                                "need", "낮음"
                        ))
                )
        ));

        mockMvc.perform(post("/api/ai/recommend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.recommendations[0].action").value("downsizing"))
                .andExpect(jsonPath("$.data.recommendations[0].kind").value("cost"))
                .andExpect(jsonPath("$.data.recommendations[0].grounds.length()").value(1));
    }

    @Test
    @DisplayName("POST /api/ai/report는 SSE로 조각을 보내고 결론을 보강한다")
    void reportStreamsMissingConclusion() throws Exception {
        String token = auth(uniqueId());
        Mockito.when(geminiClient.isAvailable()).thenReturn(true);
        Mockito.doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            onDelta.accept("# 보고서\n\n## 1. 요약\n요약 내용\n");
            return null;
        }).when(geminiClient).streamMarkdown(anyString(), anyString(), any());

        String payload = body(Map.of(
                "input", Map.of("resources", List.of(Map.of(
                        "name", "vm-a", "type", "vm", "cost", 100, "usage", "10%", "need", "낮음"
                ))),
                "recommendations", List.of(Map.of(
                        "title", "축소", "target", "vm-a", "savings", 30,
                        "grounds", List.of("사용률 낮음"), "action", "downsizing", "kind", "cost"
                ))
        ));

        MvcResult result = mockMvc.perform(post("/api/ai/report")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        String stream = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(stream).contains("## 1. 요약");
        assertThat(stream).contains("## 6. 결론");
        assertThat(stream).contains("[DONE]");
    }

    @Test
    @DisplayName("POST /api/ai/report는 스트림이 중간에 실패하면 DONE 없이 오류로 끝난다")
    void reportFailsWhenStreamBreaksMidway() throws Exception {
        String token = auth(uniqueId());
        Mockito.when(geminiClient.isAvailable()).thenReturn(true);
        Mockito.doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            onDelta.accept("# 보고서\n\n## 1. 요약\n요약 내용\n");
            throw new IOException("stream closed unexpectedly");
        }).when(geminiClient).streamMarkdown(anyString(), anyString(), any());

        String payload = body(Map.of(
                "input", Map.of("resources", List.of(Map.of(
                        "name", "vm-a", "type", "vm", "cost", 100, "usage", "10%", "need", "낮음"
                ))),
                "recommendations", List.of(Map.of(
                        "title", "축소", "target", "vm-a", "savings", 30,
                        "grounds", List.of("사용률 낮음"), "action", "downsizing", "kind", "cost"
                ))
        ));

        MvcResult result = mockMvc.perform(post("/api/ai/report")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        String stream = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(stream).contains("## 1. 요약");
        assertThat(stream).contains("REPORT_STREAM_FAILED");
        assertThat(stream).doesNotContain("[DONE]");
        assertThat(stream).doesNotContain("## 6. 결론");
    }

    @Test
    @DisplayName("POST /api/ai/report는 키가 없으면 4줄 폴백을 스트리밍한다")
    void reportFallbackWhenNoKey() throws Exception {
        String token = auth(uniqueId());
        Mockito.when(geminiClient.isAvailable()).thenReturn(false);

        String payload = body(Map.of(
                "input", Map.of("resources", List.of(Map.of(
                        "name", "vm-a", "type", "vm", "cost", 100, "usage", "10%", "need", "낮음"
                ))),
                "recommendations", List.of(Map.of(
                        "title", "축소", "target", "vm-a", "savings", 30,
                        "grounds", List.of("사용률 낮음"), "action", "downsizing", "kind", "cost"
                ))
        ));

        MvcResult result = mockMvc.perform(post("/api/ai/report")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        String stream = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(stream).contains("## 1. 요약");
        assertThat(stream).contains("## 2. 절감 제안");
        assertThat(stream).contains("## 6. 결론");
        assertThat(stream).contains("\"fallback\":true");
        assertThat(stream).contains("[DONE]");
    }

    @Test
    @DisplayName("POST /api/ai/recommend는 키가 없으면 룰 기반 폴백한다")
    void recommendFallbackWhenNoKey() throws Exception {
        String token = auth(uniqueId());
        Mockito.when(geminiClient.isAvailable()).thenReturn(false);

        String payload = body(Map.of(
                "input", Map.of(
                        "resources", List.of(Map.of(
                                "name", "old-vm",
                                "type", "vm",
                                "cost", 100,
                                "usage", "15%",
                                "need", "유휴"
                        ))
                )
        ));

        mockMvc.perform(post("/api/ai/recommend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.recommendations.length()").value(greaterThan(0)));
    }
}
