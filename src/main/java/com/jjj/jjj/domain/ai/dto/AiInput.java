package com.jjj.jjj.domain.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiInput(
        Integer limit,
        BigDecimal total,
        String purpose,
        List<AiResource> resources
) {
}
