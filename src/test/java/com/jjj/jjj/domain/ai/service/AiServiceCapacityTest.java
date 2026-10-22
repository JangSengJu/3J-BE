package com.jjj.jjj.domain.ai.service;

import com.jjj.jjj.domain.ai.client.GeminiClient;
import com.jjj.jjj.domain.ai.dto.AiInput;
import com.jjj.jjj.domain.ai.dto.AiRecommendation;
import com.jjj.jjj.domain.ai.dto.AiResource;
import com.jjj.jjj.domain.ai.dto.request.ReportRequest;
import com.jjj.jjj.global.exception.BusinessException;
import com.jjj.jjj.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceCapacityTest {

    @Test
    @DisplayName("작업 스레드가 포화되면 503으로 거절한다")
    void reportRejectedWhenExecutorSaturated() {
        GeminiClient geminiClient = mock(GeminiClient.class);
        when(geminiClient.isAvailable()).thenReturn(true);

        ExecutorService rejecting = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        ) {
            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("queue is full");
            }
        };

        AiService aiService = new AiService(geminiClient, rejecting, new ObjectMapper());

        ReportRequest request = new ReportRequest(
                new AiInput(60, new BigDecimal("100.00"), "테스트", List.of(
                        new AiResource("r1", "web-server", "EC2", new BigDecimal("100.00"), "10%", "낮음", "m5.large", "")
                )),
                List.of(new AiRecommendation("축소", "web-server", new BigDecimal("30.00"), List.of("사용률 낮음"), "downsizing", "cost"))
        );

        assertThatThrownBy(() -> aiService.report(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
