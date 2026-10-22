package com.jjj.jjj.domain.ai;

import com.jjj.jjj.domain.ai.client.GroqClient;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiCapacityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroqClient groqClient;

    @MockitoBean(name = "aiExecutor")
    private ExecutorService aiExecutor;

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("SSE 스레드가 포화되면 503을 반환한다")
    void reportReturns503WhenExecutorSaturated() throws Exception {
        String id = "user-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isCreated());

        MvcResult signin = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", id, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(signin.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();

        Mockito.doThrow(new RejectedExecutionException("queue is full")).when(aiExecutor).execute(any());

        String payload = body(Map.of(
                "input", Map.of("resources", List.of(Map.of(
                        "name", "vm-a", "type", "vm", "cost", 100, "usage", "10%", "need", "낮음"
                ))),
                "recommendations", List.of(Map.of(
                        "title", "축소", "target", "vm-a", "savings", 30,
                        "grounds", List.of("사용률 낮음"), "action", "downsizing", "kind", "cost"
                ))
        ));

        mockMvc.perform(post("/api/ai/report")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value(503));
    }
}
