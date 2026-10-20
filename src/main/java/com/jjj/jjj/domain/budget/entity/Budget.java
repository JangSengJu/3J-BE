package com.jjj.jjj.domain.budget.entity;

import com.jjj.jjj.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "budgets")
public class Budget extends BaseEntity {

    public static final int DEFAULT_LIMIT = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "budget_limit", nullable = false)
    private int limit;

    @Column(nullable = false, length = 1000)
    private String purpose;

    @ElementCollection
    @CollectionTable(name = "budget_resources", joinColumns = @JoinColumn(name = "budget_id"))
    @OrderColumn(name = "sort_order")
    private List<BudgetResource> resources = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "budget_notes", joinColumns = @JoinColumn(name = "budget_id"))
    @MapKeyColumn(name = "note_key", length = 120)
    @Column(name = "note_value", length = 500)
    private Map<String, String> notes = new LinkedHashMap<>();

    private Budget(Long memberId, int limit, String purpose) {
        this.memberId = memberId;
        this.limit = limit;
        this.purpose = purpose;
    }

    public static Budget create(Long memberId) {
        return new Budget(memberId, DEFAULT_LIMIT, "");
    }

    public void update(int limit, String purpose, List<BudgetResource> resources, Map<String, String> notes) {
        this.limit = limit;
        this.purpose = purpose;
        this.resources.clear();
        this.resources.addAll(resources);
        this.notes.clear();
        this.notes.putAll(notes);
    }
}
