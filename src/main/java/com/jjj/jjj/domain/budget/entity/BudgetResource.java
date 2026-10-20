package com.jjj.jjj.domain.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetResource {

    @Column(name = "resource_key", length = 120)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String type;

    @Column(length = 40)
    private String unit;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "resource_usage", length = 120)
    private String usage;

    @Column(name = "resource_need", length = 120)
    private String need;

    @Column(length = 200)
    private String meta;

    private BudgetResource(String id, String name, String type, String unit, BigDecimal cost,
                           String usage, String need, String meta) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.unit = unit;
        this.cost = cost;
        this.usage = usage;
        this.need = need;
        this.meta = meta;
    }

    public static BudgetResource of(String id, String name, String type, String unit, BigDecimal cost,
                                    String usage, String need, String meta) {
        return new BudgetResource(id, name, type, unit, cost, usage, need, meta);
    }
}
