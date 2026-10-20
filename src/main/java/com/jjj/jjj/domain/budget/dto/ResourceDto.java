package com.jjj.jjj.domain.budget.dto;

import java.math.BigDecimal;

public record ResourceDto(
        String id,
        String name,
        String type,
        String unit,
        BigDecimal cost,
        String usage,
        String need,
        String meta
) {
}
