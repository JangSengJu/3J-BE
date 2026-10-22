package com.jjj.jjj.domain.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class GeminiApiClient implements GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiClient.class);

    private static final String DEFAULT_FREE_MODEL = "gemini-3.8-flash";
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<String> DEFAULT_FREE_MODELS = Set.of(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview",
            "gemini-flash-latest",
            "gemini-flash-lite-latest"
    );

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final Duration deadline;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiApiClient(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:" + DEFAULT_FREE_MODEL + "}") String model,
            @Value("${gemini.allowed-models:}") List<String> allowedModels,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${gemini.timeout:60s}") Duration timeout,
            @Value("${gemini.deadline:30s}") Duration deadline,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = resolveFreeModel(model, allowedModels);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.deadline = deadline;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
            log.warn("Gemini model '{}' is not in the free-tier allowlist. Using '{}' instead.", requested, fallback);
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

        GeminiException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Duration attemptTimeout = remainingTimeout(deadlineNanos);
            if (attemptTimeout == null) {
                throw deadlineExceeded(lastError);
            }

            HttpRequest request = HttpRequest.newBuilder(endpoint(":generateContent"))
                    .timeout(attemptTimeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException e) {
                lastError = new GeminiException("Gemini 요청이 시간 초과되었습니다. timeout=" + attemptTimeout, e);
                if (attempt == MAX_ATTEMPTS || !sleepWithinDeadline(attempt, deadlineNanos)) {
                    throw lastError;
                }
                continue;
            }

            if (response.statusCode() != 200) {
                lastError = new GeminiException("Gemini 요청에 실패했습니다. status=" + response.statusCode()
                        + describe(response.body()));
                if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS
                        || !sleepWithinDeadline(attempt, deadlineNanos)) {
                    throw lastError;
                }
                continue;
            }

            String text = extractText(objectMapper.readTree(response.body()), true);
            if (text.isBlank()) {
                throw new GeminiException("Gemini 응답이 비어 있습니다.");
            }
            return text;
        }
        throw lastError;
    }

    @Override
    public void streamMarkdown(String systemInstruction, String userPrompt, Consumer<String> onDelta) throws Exception {
        String body = buildBody(systemInstruction, userPrompt, false);

        GeminiException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(endpoint(":streamGenerateContent", "alt=sse"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String error;
                try (InputStream input = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    error = reader.lines().limit(20).collect(Collectors.joining("\n"));
                }
                lastError = new GeminiException("Gemini 스트리밍 요청에 실패했습니다. status=" + response.statusCode()
                        + describe(error));
                if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                    throw lastError;
                }
                backoff(attempt);
                continue;
            }

            try (InputStream input = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }

                    String delta = extractText(objectMapper.readTree(data), false);
                    if (!delta.isEmpty()) {
                        onDelta.accept(delta);
                    }
                }
            }
            return;
        }
        throw lastError;
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

    private GeminiException deadlineExceeded(GeminiException lastError) {
        return new GeminiException("Gemini 요청이 데드라인(" + deadline + ")을 초과했습니다."
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

    private URI endpoint(String method) {
        return endpoint(method, null);
    }

    private URI endpoint(String method, String query) {
        String url = baseUrl + "/models/" + model + method;
        if (query != null && !query.isBlank()) {
            url = url + "?" + query;
        }
        return URI.create(url);
    }

    private String buildBody(String systemInstruction, String userPrompt, boolean json) throws Exception {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", json ? 0.2 : 0.4);
        generationConfig.put("maxOutputTokens", json ? 4096 : 8192);
        if (json) {
            generationConfig.put("responseMimeType", "application/json");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt))
        )));
        body.put("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(body);
    }

    private String extractText(JsonNode root, boolean required) {
        StringBuilder text = new StringBuilder();
        JsonNode candidates = root == null ? null : root.get("candidates");
        if (candidates != null && candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate.path("content").path("parts");
                for (JsonNode part : parts) {
                    JsonNode value = part.get("text");
                    if (value != null && !value.isNull()) {
                        text.append(value.asString());
                    }
                }
            }
        }

        if (required && text.isEmpty()) {
            throw new GeminiException("Gemini 응답에 텍스트가 없습니다.");
        }
        return text.toString();
    }
}
