package com.jjj.jjj.domain.budget.repository;

import com.jjj.jjj.domain.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByMemberId(Long memberId);
}
