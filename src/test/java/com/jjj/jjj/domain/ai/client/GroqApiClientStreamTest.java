package com.jjj.jjj.domain.ai.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqApiClientStreamTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GroqApiClient client(Duration idleTimeout) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(null);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            // 응답 헤더만 보내고 데이터는 보내지 않는다 (스트림 정지 상태)
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();

        return new GroqApiClient(
                "test-key",
                "openai/gpt-oss-120b",
                List.of("openai/gpt-oss-120b"),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                idleTimeout,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("데이터가 오지 않으면 유휴 제한 시간 후 스트림을 닫는다")
    void idleStreamIsClosed() throws Exception {
        GroqApiClient client = client(Duration.ofMillis(500));

        long started = System.currentTimeMillis();
        assertThatThrownBy(() -> client.streamMarkdown("system", "prompt", delta -> { }))
                .isInstanceOf(Exception.class);
        assertThat(System.currentTimeMillis() - started).isLessThan(5_000);
    }

    @Test
    @DisplayName("스트림이 정상 종료되면 유휴 감시 자원을 정리한다")
    void streamCompletesNormally() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.close();
        });
        server.start();

        GroqApiClient client = new GroqApiClient(
                "test-key",
                "openai/gpt-oss-120b",
                List.of("openai/gpt-oss-120b"),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                new ObjectMapper()
        );

        List<String> deltas = new ArrayList<>();
        client.streamMarkdown("system", "prompt", deltas::add);

        assertThat(deltas).containsExactly("hello");
    }
}
