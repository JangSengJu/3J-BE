package com.jjj.jjj.domain.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class GroqApiClient implements GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqApiClient.class);

    private static final String DEFAULT_FREE_MODEL = "openai/gpt-oss-120b";
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<String> DEFAULT_FREE_MODELS = Set.of(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3.8-27b"
    );

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final Duration deadline;
    private final Duration streamIdleTimeout;
    private final ScheduledExecutorService idleWatchdog;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqApiClient(
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:" + DEFAULT_FREE_MODEL + "}") String model,
            @Value("${groq.allowed-models:}") List<String> allowedModels,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.timeout:60s}") Duration timeout,
            @Value("${groq.deadline:30s}") Duration deadline,
            @Value("${groq.stream-idle-timeout:30s}") Duration streamIdleTimeout,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = resolveFreeModel(model, allowedModels);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.deadline = deadline;
        this.streamIdleTimeout = streamIdleTimeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "groq-stream-idle-guard");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.setRemoveOnCancelPolicy(true);
        this.idleWatchdog = watchdog;
    }

    private static String resolveFreeModel(String configured, List<String> allowedModels) {
        Set<String> allowed = allowedModels == null ? Set.of() : allowedModels.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (allowed.isEmpty()) {
            allowed = DEFAULT_FREE_MODELS;
        }

        String requested = configured == null ? "" : configured.trim();
        if (allowed.contains(requested)) {
            return requested;
        }

        String fallback = allowed.contains(DEFAULT_FREE_MODEL) ? DEFAULT_FREE_MODEL : allowed.iterator().next();
        if (!requested.isEmpty()) {
            log.warn("Groq model '{}' is not in the free-tier allowlist. Using '{}' instead.", requested, fallback);
        }
        return fallback;
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String generateJson(String systemInstruction, String userPrompt) throws Exception {
        String body = buildBody(systemInstruction, userPrompt, true);
        long deadlineNanos = System.nanoTime() + deadline.toNanos();

        GroqException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Duration attemptTimeout = remainingTimeout(deadlineNanos);
            if (attemptTimeout == null) {
                throw deadlineExceeded(lastError);
            }

            HttpRequest request = HttpRequest.newBuilder(endpoint("/chat/completions"))
                    .timeout(attemptTimeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException e) {
                lastError = new GroqException("Groq 요청이 시간 초과되었습니다. timeout=" + attemptTimeout, e);
                if (attempt == MAX_ATTEMPTS || !sleepWithinDeadline(attempt, deadlineNanos)) {
                    throw lastError;
                }
                continue;
            }

            if (response.statusCode() != 200) {
                lastError = new GroqException("Groq 요청에 실패했습니다. status=" + response.statusCode()
                        + describe(response.body()));
                if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS
                        || !sleepWithinDeadline(attempt, deadlineNanos)) {
                    throw lastError;
                }
                continue;
            }

            String text = extractText(objectMapper.readTree(response.body()));
            if (text.isBlank()) {
                throw new GroqException("Groq 응답이 비어 있습니다.");
            }
            return text;
        }
        throw lastError;
    }

    @Override
    public void streamMarkdown(String systemInstruction, String userPrompt, Consumer<String> onDelta) throws Exception {
        String body = buildBody(systemInstruction, userPrompt, false);

        GroqException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(endpoint("/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String error;
                try (InputStream input = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    error = reader.lines().limit(20).collect(Collectors.joining("\n"));
                }
                lastError = new GroqException("Groq 스트리밍 요청에 실패했습니다. status=" + response.statusCode()
                        + describe(error));
                if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                    throw lastError;
                }
                backoff(attempt);
                continue;
            }

            try (InputStream input = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                ScheduledFuture<?> idleGuard = scheduleIdleGuard(input);
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        idleGuard = renewIdleGuard(idleGuard, input);
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring("data:".length()).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) {
                            continue;
                        }

                        String delta = extractDelta(objectMapper.readTree(data));
                        if (!delta.isEmpty()) {
                            onDelta.accept(delta);
                        }
                    }
                } finally {
                    idleGuard.cancel(false);
                    closeQuietly(input);
                }
            }
            return;
        }
        throw lastError;
    }

    private ScheduledFuture<?> scheduleIdleGuard(InputStream input) {
        return idleWatchdog.schedule(() -> closeQuietly(input), streamIdleTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private ScheduledFuture<?> renewIdleGuard(ScheduledFuture<?> previous, InputStream input) {
        previous.cancel(false);
        return scheduleIdleGuard(input);
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // stream already closed
        }
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private Duration remainingTimeout(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return null;
        }
        Duration remaining = Duration.ofNanos(remainingNanos);
        return timeout.compareTo(remaining) > 0 ? remaining : timeout;
    }

    private boolean sleepWithinDeadline(int attempt, long deadlineNanos) throws InterruptedException {
        long backoffMillis = 500L * attempt;
        if (System.nanoTime() + backoffMillis * 1_000_000L >= deadlineNanos) {
            return false;
        }
        Thread.sleep(backoffMillis);
        return true;
    }

    private GroqException deadlineExceeded(GroqException lastError) {
        return new GroqException("Groq 요청이 데드라인(" + deadline + ")을 초과했습니다."
                + (lastError == null ? "" : " 마지막 오류: " + lastError.getMessage()));
    }

    private static String describe(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    private static void backoff(int attempt) throws InterruptedException {
        Thread.sleep(500L * attempt);
    }

    private URI endpoint(String path) {
        return URI.create(baseUrl + path);
    }

    private String buildBody(String systemInstruction, String userPrompt, boolean json) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", json ? 0.2 : 0.4);
        body.put("max_tokens", 8192);
        body.put("stream", !json);
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemInstruction));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);

        return objectMapper.writeValueAsString(body);
    }

    private String extractText(JsonNode root) {
        JsonNode message = root == null ? null : root.path("choices").path(0).path("message");
        JsonNode content = message.path("content");
        if (content.isTextual()) {
            return content.asString();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                JsonNode value = part.get("text");
                if (value != null && value.isTextual()) {
                    text.append(value.asString());
                }
            }
            return text.toString();
        }
        throw new GroqException("Groq 응답에 텍스트가 없습니다.");
    }

    private String extractDelta(JsonNode root) {
        JsonNode delta = root == null ? null : root.path("choices").path(0).path("delta");
        JsonNode content = delta.path("content");
        if (content.isTextual()) {
            return content.asString();
        }
        return "";
    }
}
