package com.jjj.jjj.domain.budget.service;

import com.jjj.jjj.domain.budget.dto.BudgetDto;
import com.jjj.jjj.domain.budget.dto.ResourceDto;
import com.jjj.jjj.domain.budget.entity.Budget;
import com.jjj.jjj.domain.budget.entity.BudgetResource;
import com.jjj.jjj.domain.budget.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final int MAX_RESOURCES = 200;
    private static final int MAX_NOTES = 200;
    private static final int MAX_NAME = 120;
    private static final int MAX_TEXT = 120;
    private static final int MAX_META = 200;
    private static final int MAX_NOTE = 500;
    private static final int MAX_PURPOSE = 1000;
    private static final BigDecimal MAX_COST = new BigDecimal("9999999999.99");

    private static final Set<String> UNITS = Set.of(
            "hour", "lcu_hour", "month", "gb_month", "gb_transfer",
            "gb_second", "million_requests", "request"
    );

    private final BudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public BudgetDto getBudget(Long memberId) {
        return budgetRepository.findByMemberId(memberId)
                .map(this::toDto)
                .orElseGet(BudgetService::emptyBudget);
    }

    @Transactional
    public BudgetDto saveBudget(Long memberId, BudgetDto request) {
        Budget budget = budgetRepository.findByMemberId(memberId)
                .orElseGet(() -> Budget.create(memberId));

        int limit = normalizeLimit(request.limit(), budget.getLimit());
        String purpose = defaultIfNull(normalizeText(request.purpose(), MAX_PURPOSE), "");
        List<BudgetResource> resources = normalizeResources(request.resources());
        Map<String, String> notes = normalizeNotes(request.notes());

        budget.update(limit, purpose, resources, notes);
        budgetRepository.save(budget);

        return toDto(budget);
    }

    private List<BudgetResource> normalizeResources(List<ResourceDto> resources) {
        if (resources == null) {
            return List.of();
        }

        List<BudgetResource> normalized = new ArrayList<>();
        for (ResourceDto resource : resources) {
            if (normalized.size() >= MAX_RESOURCES) {
                break;
            }
            if (resource == null) {
                continue;
            }

            String name = normalizeText(resource.name(), MAX_NAME);
            if (name == null) {
                continue;
            }

            String id = normalizeText(resource.id(), MAX_TEXT);
            if (id == null) {
                id = "r-" + (normalized.size() + 1);
            }

            normalized.add(BudgetResource.of(
                    id,
                    name,
                    defaultIfNull(normalizeText(resource.type(), MAX_TEXT), "기타"),
                    normalizeUnit(resource.unit()),
                    normalizeCost(resource.cost()),
                    defaultIfNull(normalizeText(resource.usage(), MAX_TEXT), "미상"),
                    defaultIfNull(normalizeText(resource.need(), MAX_TEXT), "미상"),
                    defaultIfNull(normalizeText(resource.meta(), MAX_META), "")
            ));
        }
        return normalized;
    }

    private Map<String, String> normalizeNotes(Map<String, String> notes) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (notes == null) {
            return normalized;
        }

        for (Map.Entry<String, String> entry : notes.entrySet()) {
            if (normalized.size() >= MAX_NOTES) {
                break;
            }

            String normalizedKey = normalizeText(entry.getKey(), MAX_NAME);
            if (normalizedKey == null) {
                continue;
            }
            normalized.put(normalizedKey, defaultIfNull(normalizeText(entry.getValue(), MAX_NOTE), ""));
        }
        return normalized;
    }

    private int normalizeLimit(Integer limit, int fallback) {
        if (limit == null || limit <= 0) {
            return fallback;
        }
        return limit;
    }

    private String normalizeUnit(String unit) {
        String normalized = normalizeText(unit, 40);
        if (normalized == null || !UNITS.contains(normalized)) {
            return "month";
        }
        return normalized;
    }

    private BigDecimal normalizeCost(BigDecimal cost) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (cost == null) {
            return zero;
        }

        BigDecimal rounded = cost.setScale(2, RoundingMode.HALF_UP);
        if (rounded.abs().compareTo(MAX_COST) > 0) {
            return zero;
        }
        return rounded;
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

    private BudgetDto toDto(Budget budget) {
        List<ResourceDto> resources = budget.getResources().stream()
                .map(resource -> new ResourceDto(
                        resource.getId(),
                        resource.getName(),
                        resource.getType(),
                        resource.getUnit(),
                        resource.getCost(),
                        resource.getUsage(),
                        resource.getNeed(),
                        resource.getMeta()
                ))
                .toList();

        return new BudgetDto(
                resources,
                budget.getLimit(),
                new LinkedHashMap<>(budget.getNotes()),
                budget.getPurpose()
        );
    }

    private static BudgetDto emptyBudget() {
        return new BudgetDto(List.of(), Budget.DEFAULT_LIMIT, Map.of(), "");
    }
}
