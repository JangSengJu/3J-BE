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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqApiClientDeadlineTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GroqApiClient client(Duration timeout, Duration deadline, long delayMillis) throws IOException {
        return client(timeout, deadline, Duration.ofSeconds(30), delayMillis);
    }

    private GroqApiClient client(Duration timeout, Duration deadline, Duration idleTimeout, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        return new GroqApiClient(
                "test-key",
                "openai/gpt-oss-120b",
                List.of("openai/gpt-oss-120b"),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                timeout,
                deadline,
                idleTimeout,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("데드라인이 지나면 즉시 예외가 발생한다")
    void deadlineExceeded() throws Exception {
        GroqApiClient client = client(Duration.ofSeconds(30), Duration.ofMillis(300), 5_000);

        long started = System.currentTimeMillis();
        assertThatThrownBy(() -> client.generateJson("system", "prompt"))
                .isInstanceOf(GroqException.class);
        assertThat(System.currentTimeMillis() - started).isLessThan(4_000);
    }

    @Test
    @DisplayName("응답이 빠르면 데드라인 안에서 정상 처리한다")
    void fastResponseWithinDeadline() throws Exception {
        GroqApiClient client = client(Duration.ofSeconds(30), Duration.ofSeconds(10), 0);

        assertThat(client.generateJson("system", "prompt")).isEqualTo("ok");
    }
}
