package com.jjj.jjj.domain.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiRecommendation(
        String title,
        String target,
        BigDecimal savings,
        List<String> grounds,
        String action,
        String kind
) {
}
