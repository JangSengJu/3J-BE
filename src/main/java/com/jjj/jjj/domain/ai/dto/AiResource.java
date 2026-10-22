package com.jjj.jjj.domain.ai.dto;

import java.math.BigDecimal;

public record AiResource(
        String id,
        String name,
        String type,
        BigDecimal cost,
        String usage,
        String need,
        String meta,
        String note
) {
}
