package com.jjj.jjj.domain.ai.service;

import com.jjj.jjj.domain.ai.client.GroqClient;
import com.jjj.jjj.domain.ai.dto.AiInput;
import com.jjj.jjj.domain.ai.dto.AiRecommendation;
import com.jjj.jjj.domain.ai.dto.AiResource;
import com.jjj.jjj.domain.ai.dto.request.ReportRequest;
import com.jjj.jjj.domain.ai.dto.response.RecommendResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiServicePromptTest {

    private final AtomicReference<String> recommendPrompt = new AtomicReference<>();
    private final AtomicReference<String> reportPrompt = new AtomicReference<>();
    private final CountDownLatch reportStarted = new CountDownLatch(1);

    private GroqClient groqClient() throws Exception {
        GroqClient client = Mockito.mock(GroqClient.class);
        Mockito.when(client.isAvailable()).thenReturn(true);
        Mockito.when(client.generateJson(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    recommendPrompt.set(invocation.getArgument(1));
                    return "{\"recommendations\":[{\"title\":\"축소\",\"target\":\"web-server\",\"savings\":10,"
                            + "\"grounds\":[\"사용률 낮음\"],\"action\":\"downsizing\",\"kind\":\"cost\"}]}";
                });
        Mockito.doAnswer(invocation -> {
            reportPrompt.set(invocation.getArgument(1));
            reportStarted.countDown();
            Consumer<String> onDelta = invocation.getArgument(2);
            onDelta.accept("## 1. 요약\n");
            onDelta.accept("## 6. 결론\n");
            return null;
        }).when(client).streamMarkdown(Mockito.anyString(), Mockito.anyString(), Mockito.any());
        return client;
    }

    private AiService service(GroqClient client) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return new AiService(client, executor, new ObjectMapper());
    }

    private static AiInput input() {
        return new AiInput(
                60,
                new BigDecimal("90.30"),
                "비용 절감",
                List.of(new AiResource("r-1", "web-server", "ec2", new BigDecimal("70.08"), "4%", "낮음", null, null))
        );
    }

    @Test
    @DisplayName("추천 프롬프트는 JSON 응답을 요청한다")
    void recommendPromptAsksJson() throws Exception {
        AiService service = service(groqClient());

        RecommendResponse response = service.recommend(input());

        assertThat(response.fallback()).isFalse();
        assertThat(recommendPrompt.get()).contains("JSON으로만 응답");
    }

    @Test
    @DisplayName("리포트 프롬프트는 JSON 응답을 요구하지 않는다")
    void reportPromptDoesNotAskJson() throws Exception {
        AiService service = service(groqClient());

        SseEmitter emitter = service.report(new ReportRequest(
                input(),
                List.of(new AiRecommendation("축소", "web-server", new BigDecimal("10.00"),
                        List.of("사용률 낮음"), "downsizing", "cost"))
        ));

        assertThat(emitter).isNotNull();
        assertThat(reportStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reportPrompt.get())
                .contains("6개 섹션 마크다운 보고서")
                .doesNotContain("JSON으로만 응답");
    }
}
