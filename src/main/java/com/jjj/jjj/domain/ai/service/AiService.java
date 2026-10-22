package com.jjj.jjj.domain.ai.service;

import com.jjj.jjj.domain.ai.client.GeminiClient;
import com.jjj.jjj.domain.ai.dto.AiInput;
import com.jjj.jjj.domain.ai.dto.AiRecommendation;
import com.jjj.jjj.domain.ai.dto.AiResource;
import com.jjj.jjj.domain.ai.dto.request.ReportRequest;
import com.jjj.jjj.domain.ai.dto.response.RecommendResponse;
import com.jjj.jjj.global.exception.BusinessException;
import com.jjj.jjj.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;
    private static final int MAX_RECOMMENDATIONS = 15;
    private static final int MAX_RESOURCES = 200;
    private static final int MAX_GROUNDS = 5;
    private static final int MAX_TITLE = 200;
    private static final int MAX_GROUND = 400;
    private static final int MAX_TEXT = 120;
    private static final int MAX_META = 200;
    private static final int MAX_NOTE = 500;
    private static final int MAX_PURPOSE = 1000;

    private static final String CONCLUSION_HEADING = "## 6. 결론";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal THIRTY_PERCENT = new BigDecimal("0.30");
    private static final BigDecimal FIFTEEN_PERCENT = new BigDecimal("0.15");
    private static final BigDecimal FORTY_PERCENT = new BigDecimal("0.40");

    private static final Pattern USAGE_PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");

    private static final Set<String> KINDS = Set.of(
            "cost", "performance", "reliability", "security",
            "scalability", "architecture", "other"
    );
    private static final Set<String> ACTIONS = Set.of(
            "downsizing", "deletion", "migration", "reserved", "consolidation",
            "scaleup", "tier", "rightsizing", "other"
    );

    private static final String RECOMMEND_SYSTEM = """
            당신은 클라우드 비용 최적화 전문가입니다.
            제공된 리소스, 월 예산 한도, 용도, 요금 정보를 분석해 절감 방안을 제안하세요.
            반드시 아래 JSON 스키마로만 응답하고, 설명이나 코드블록을 붙이지 마세요.
            {"recommendations":[{"title":"...","target":"...","savings":0.0,"grounds":["..."],"action":"downsizing","kind":"cost"}]}
            - recommendations: 최대 15개
            - title: 한국어, 200자 이내
            - target: 대상 리소스 이름
            - savings: 양수=월 절감액(USD), 음수=추가 투자, 0=변동 없음
            - grounds: 최대 5개, 각 400자 이내
            - action: downsizing|deletion|migration|reserved|consolidation|scaleup|tier|rightsizing|other
            - kind: cost|performance|reliability|security|scalability|architecture|other
            """;

    private static final String REPORT_SYSTEM = """
            당신은 클라우드 아키텍처 컨설턴트입니다.
            한국어 마크다운 개선 보고서를 작성하세요. 아래 6개 섹션을 순서대로 반드시 포함하세요.
            # 클라우드 아키텍처 개선 보고서
            ## 1. 요약
            ## 2. 절감 제안
            ## 3. 리스크
            ## 4. 실행 계획
            ## 5. 예상 효과
            ## 6. 결론
            제공된 데이터에 없는 내용은 추측하지 말고 간결하게 작성하세요.
            """;

    private final GeminiClient geminiClient;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    public AiService(
            GeminiClient geminiClient,
            @Qualifier("aiExecutor") ExecutorService executor,
            ObjectMapper objectMapper
    ) {
        this.geminiClient = geminiClient;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public RecommendResponse recommend(AiInput input) {
        List<AiResource> resources = sanitizeResources(input == null ? null : input.resources());

        if (resources.isEmpty() || !geminiClient.isAvailable()) {
            return new RecommendResponse(ruleBased(input, resources), true);
        }

        try {
            String raw = geminiClient.generateJson(RECOMMEND_SYSTEM, buildRecommendPrompt(input, resources));
            List<AiRecommendation> recommendations = normalizeRecommendations(parseRecommendations(raw));
            if (recommendations.isEmpty()) {
                return new RecommendResponse(ruleBased(input, resources), true);
            }
            return new RecommendResponse(recommendations, false);
        } catch (Exception e) {
            log.warn("Gemini recommend failed, using rule-based fallback: {}", e.getMessage());
            return new RecommendResponse(ruleBased(input, resources), true);
        }
    }

    public SseEmitter report(ReportRequest request) {
        AiInput input = request == null ? null : request.input();
        List<AiResource> resources = sanitizeResources(input == null ? null : input.resources());
        List<AiRecommendation> recommendations = normalizeRecommendations(
                request == null || request.recommendations() == null
                        ? objectMapper.valueToTree(List.of())
                        : objectMapper.valueToTree(request.recommendations())
        );

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        emitter.onError(error -> cancelled.set(true));

        boolean fallbackNeeded = input == null
                || resources.isEmpty()
                || recommendations.isEmpty()
                || !geminiClient.isAvailable();

        try {
            executor.execute(() -> executeReport(emitter, input, resources, recommendations, cancelled, fallbackNeeded));
        } catch (RejectedExecutionException e) {
            log.warn("AI report rejected, executor is saturated: {}", e.getMessage());
            emitter.completeWithError(e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return emitter;
    }

    private void executeReport(
            SseEmitter emitter,
            AiInput input,
            List<AiResource> resources,
            List<AiRecommendation> recommendations,
            AtomicBoolean cancelled,
            boolean fallbackNeeded
    ) {
        try {
            if (fallbackNeeded) {
                sendFallbackReport(emitter, input, resources, recommendations, cancelled);
            } else {
                streamAiReport(emitter, input, resources, recommendations, cancelled);
            }

            if (!cancelled.get()) {
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            }
        } catch (CancellationException e) {
            log.debug("Report stream cancelled by client");
        } catch (Exception e) {
            if (!cancelled.get()) {
                log.warn("Report stream failed: {}", e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // response already closed
                }
            }
        }
    }

    private void streamAiReport(
            SseEmitter emitter,
            AiInput input,
            List<AiResource> resources,
            List<AiRecommendation> recommendations,
            AtomicBoolean cancelled
    ) {
        StringBuilder collected = new StringBuilder();
        boolean failed = false;

        try {
            geminiClient.streamMarkdown(REPORT_SYSTEM, buildReportPrompt(input, resources, recommendations), delta -> {
                if (cancelled.get()) {
                    throw new CancellationException("client disconnected");
                }
                collected.append(delta);
                sendDelta(emitter, delta, null);
            });
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            failed = true;
            log.warn("Gemini report streaming failed: {}", e.getMessage());
        }

        if (cancelled.get()) {
            throw new CancellationException("client disconnected");
        }

        if (failed && collected.isEmpty()) {
            sendFallbackReport(emitter, input, resources, recommendations, cancelled);
            return;
        }

        if (!collected.toString().contains(CONCLUSION_HEADING)) {
            sendConclusion(emitter, input, resources, recommendations, cancelled);
        }
    }

    private void sendFallbackReport(
            SseEmitter emitter,
            AiInput input,
            List<AiResource> resources,
            List<AiRecommendation> recommendations,
            AtomicBoolean cancelled
    ) {
        String[] lines = {
                "# 클라우드 아키텍처 개선 보고서\n\n",
                "## 1. 요약\n" + summaryLine(input, resources) + "\n\n",
                "## 2. 절감 제안\n" + proposalLine(recommendations) + "\n\n",
                "## 6. 결론\n" + conclusionLine(recommendations) + "\n"
        };

        for (String line : lines) {
            if (cancelled.get()) {
                throw new CancellationException("client disconnected");
            }
            sleep(25);
            sendDelta(emitter, line, true);
        }
    }

    private void sendConclusion(
            SseEmitter emitter,
            AiInput input,
            List<AiResource> resources,
            List<AiRecommendation> recommendations,
            AtomicBoolean cancelled
    ) {
        if (cancelled.get()) {
            throw new CancellationException("client disconnected");
        }
        sendDelta(emitter, "\n\n" + CONCLUSION_HEADING + "\n" + conclusionLine(recommendations) + "\n", true);
    }

    private void sendDelta(SseEmitter emitter, String delta, Boolean fallback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delta", delta);
        if (fallback != null) {
            payload.put("fallback", fallback);
        }

        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            throw new CancellationException("client disconnected");
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted");
        }
    }

    private String summaryLine(AiInput input, List<AiResource> resources) {
        BigDecimal total = resolvedTotal(input, resources);
        String limit = input == null || input.limit() == null ? "미설정" : input.limit() + " USD";
        return "현재 리소스 " + resources.size() + "건, 월 예상 요금 "
                + total.toPlainString() + " USD(한도 " + limit + ")입니다.";
    }

    private String proposalLine(List<AiRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return "절감 가능한 항목이 확인되지 않았습니다.";
        }
        AiRecommendation first = recommendations.get(0);
        return first.title() + " — 예상 절감 " + first.savings().toPlainString() + " USD";
    }

    private String conclusionLine(List<AiRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return "입력 데이터가 부족해 추가 절감 여지를 판단하기 어렵습니다. 사용률 데이터를 보강해 다시 분석하세요.";
        }
        return "위 제안을 우선순위대로 적용하면 불필요한 지출을 줄이고 운영 안정성을 유지할 수 있습니다.";
    }

    private List<AiRecommendation> ruleBased(AiInput input, List<AiResource> resources) {
        BigDecimal total = resolvedTotal(input, resources);
        BigDecimal threshold = total.multiply(FORTY_PERCENT);
        List<AiRecommendation> result = new ArrayList<>();

        for (AiResource resource : resources) {
            if (result.size() >= MAX_RECOMMENDATIONS) {
                break;
            }

            BigDecimal cost = resource.cost();
            if (cost == null || cost.signum() <= 0) {
                continue;
            }

            String combined = ((resource.usage() == null ? "" : resource.usage()) + " "
                    + (resource.need() == null ? "" : resource.need())).toLowerCase(Locale.ROOT);
            double usagePercent = parseUsagePercent(resource.usage());

            if (combined.contains("미사용") || combined.contains("유휴") || combined.contains("idle")) {
                result.add(recommendation(
                        resource.name() + " 삭제 검토",
                        resource.name(),
                        cost,
                        "사용 흔적이 거의 없어 리소스를 제거하면 비용을 절감할 수 있습니다.",
                        "deletion"
                ));
            } else if (usagePercent >= 0 && usagePercent < 30) {
                result.add(recommendation(
                        resource.name() + " 사양 축소",
                        resource.name(),
                        cost.multiply(THIRTY_PERCENT).setScale(2, RoundingMode.HALF_UP),
                        String.format(Locale.ROOT, "사용률 %.0f%%로 용도 대비 과한 사양입니다.", usagePercent),
                        "downsizing"
                ));
            } else if (threshold.signum() > 0 && cost.compareTo(threshold) >= 0) {
                result.add(recommendation(
                        resource.name() + " 비용 최적화",
                        resource.name(),
                        cost.multiply(FIFTEEN_PERCENT).setScale(2, RoundingMode.HALF_UP),
                        "전체 비용에서 차지하는 비중이 커 우선 최적화 대상입니다.",
                        "rightsizing"
                ));
            }
        }

        return result;
    }

    private AiRecommendation recommendation(String title, String target, BigDecimal savings, String ground, String action) {
        return new AiRecommendation(
                title,
                target,
                savings.setScale(2, RoundingMode.HALF_UP),
                List.of(ground),
                action,
                "cost"
        );
    }

    private static double parseUsagePercent(String usage) {
        if (usage == null) {
            return -1;
        }
        Matcher matcher = USAGE_PERCENT.matcher(usage);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private BigDecimal resolvedTotal(AiInput input, List<AiResource> resources) {
        if (input != null && input.total() != null && input.total().signum() >= 0) {
            return input.total().setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (AiResource resource : resources) {
            if (resource.cost() != null) {
                sum = sum.add(resource.cost());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private List<AiResource> sanitizeResources(List<AiResource> resources) {
        if (resources == null) {
            return List.of();
        }

        List<AiResource> result = new ArrayList<>();
        for (AiResource resource : resources) {
            if (result.size() >= MAX_RESOURCES) {
                break;
            }
            if (resource == null) {
                continue;
            }

            String name = normalizeText(resource.name(), MAX_TEXT);
            if (name == null) {
                continue;
            }

            BigDecimal cost = resource.cost() == null
                    ? ZERO
                    : resource.cost().setScale(2, RoundingMode.HALF_UP);

            result.add(new AiResource(
                    defaultIfNull(normalizeText(resource.id(), MAX_TEXT), "r-" + (result.size() + 1)),
                    name,
                    defaultIfNull(normalizeText(resource.type(), MAX_TEXT), "기타"),
                    cost,
                    defaultIfNull(normalizeText(resource.usage(), MAX_TEXT), "미상"),
                    defaultIfNull(normalizeText(resource.need(), MAX_TEXT), "미상"),
                    defaultIfNull(normalizeText(resource.meta(), MAX_META), ""),
                    defaultIfNull(normalizeText(resource.note(), MAX_NOTE), "")
            ));
        }
        return result;
    }

    private List<AiRecommendation> normalizeRecommendations(JsonNode node) {
        JsonNode list = node;
        if (list != null && list.isObject()) {
            list = list.get("recommendations");
        }
        if (list == null || !list.isArray()) {
            return List.of();
        }

        List<AiRecommendation> result = new ArrayList<>();
        for (JsonNode item : list) {
            if (result.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            if (item == null || !item.isObject()) {
                continue;
            }

            String title = normalizeText(jsonText(item.get("title")), MAX_TITLE);
            if (title == null) {
                title = "제안 " + (result.size() + 1);
            }

            result.add(new AiRecommendation(
                    title,
                    defaultIfNull(normalizeText(jsonText(item.get("target")), MAX_TEXT), ""),
                    normalizeSavings(item.get("savings")),
                    normalizeGrounds(item.get("grounds")),
                    normalizeEnum(jsonText(item.get("action")), ACTIONS, "other"),
                    normalizeEnum(jsonText(item.get("kind")), KINDS, "cost")
            ));
        }
        return result;
    }

    private JsonNode parseRecommendations(String raw) {
        String cleaned = stripCodeFence(raw == null ? "" : raw.trim());

        JsonNode parsed = tryParse(cleaned);
        if (parsed == null) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                parsed = tryParse(cleaned.substring(start, end + 1));
            }
        }
        if (parsed == null) {
            throw new IllegalArgumentException("AI 응답을 해석할 수 없습니다.");
        }
        return parsed;
    }

    private JsonNode tryParse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstNewline = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return value;
        }
        return value.substring(firstNewline + 1, lastFence).trim();
    }

    private static BigDecimal normalizeSavings(JsonNode node) {
        if (node == null || node.isNull()) {
            return ZERO;
        }
        try {
            BigDecimal value = node.isNumber()
                    ? node.decimalValue()
                    : new BigDecimal(node.asString().trim());
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return ZERO;
        }
    }

    private static List<String> normalizeGrounds(JsonNode node) {
        List<String> grounds = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (grounds.size() >= MAX_GROUNDS) {
                    break;
                }
                String value = normalizeText(jsonText(item), MAX_GROUND);
                if (value != null) {
                    grounds.add(value);
                }
            }
        }
        return grounds.isEmpty() ? List.of("AI 분석 결과") : List.copyOf(grounds);
    }

    private static String jsonText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isString() ? node.asString() : node.toString();
    }

    private static String normalizeEnum(String value, Set<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String buildRecommendPrompt(AiInput input, List<AiResource> resources) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("월 예산 한도: ")
                .append(input == null || input.limit() == null ? "미설정" : input.limit())
                .append('\n');
        prompt.append("현재 월 예상 요금 합계: ")
                .append(resolvedTotal(input, resources).toPlainString())
                .append(" USD\n");
        prompt.append("용도: ")
                .append(input == null ? "" : defaultIfNull(normalizeText(input.purpose(), MAX_PURPOSE), ""))
                .append("\n\n");

        appendResources(prompt, resources);
        prompt.append("\n위 데이터를 분석해 절감 추천을 JSON으로만 응답하세요.");
        return prompt.toString();
    }

    private String buildReportPrompt(AiInput input, List<AiResource> resources, List<AiRecommendation> recommendations) {
        StringBuilder prompt = new StringBuilder(buildRecommendPrompt(input, resources));
        prompt.append("\n\n추천 결과:\n");
        for (int i = 0; i < recommendations.size(); i++) {
            AiRecommendation recommendation = recommendations.get(i);
            prompt.append(i + 1).append(". title=").append(recommendation.title())
                    .append(" | target=").append(recommendation.target())
                    .append(" | savings=").append(recommendation.savings().toPlainString())
                    .append(" | action=").append(recommendation.action())
                    .append(" | kind=").append(recommendation.kind())
                    .append(" | grounds=").append(String.join(" / ", recommendation.grounds()))
                    .append('\n');
        }
        prompt.append("\n위 데이터로 6개 섹션 마크다운 보고서를 작성하세요.");
        return prompt.toString();
    }

    private static void appendResources(StringBuilder prompt, List<AiResource> resources) {
        prompt.append("리소스:\n");
        if (resources.isEmpty()) {
            prompt.append("(없음)\n");
            return;
        }
        for (int i = 0; i < resources.size(); i++) {
            AiResource resource = resources.get(i);
            prompt.append(i + 1).append(". name=").append(resource.name())
                    .append(" | type=").append(resource.type())
                    .append(" | cost=").append(resource.cost().toPlainString())
                    .append(" | usage=").append(resource.usage())
                    .append(" | need=").append(resource.need())
                    .append(" | meta=").append(resource.meta());
            if (!resource.note().isEmpty()) {
                prompt.append(" | note=").append(resource.note());
            }
            prompt.append('\n');
        }
    }

    private static String normalizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String defaultIfNull(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
