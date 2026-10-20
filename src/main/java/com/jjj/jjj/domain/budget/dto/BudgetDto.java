package com.jjj.jjj.domain.budget.dto;

import java.util.List;
import java.util.Map;

public record BudgetDto(
        List<ResourceDto> resources,
        Integer limit,
        Map<String, String> notes,
        String purpose
) {
}
