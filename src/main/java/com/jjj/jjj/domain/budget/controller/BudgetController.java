package com.jjj.jjj.domain.budget.controller;

import com.jjj.jjj.domain.budget.dto.BudgetDto;
import com.jjj.jjj.domain.budget.dto.response.BudgetResponse;
import com.jjj.jjj.domain.budget.service.BudgetService;
import com.jjj.jjj.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<ApiResponse<BudgetResponse>> getBudget(Authentication authentication) {
        Long memberId = Long.valueOf(authentication.getName());

        return ResponseEntity.ok(ApiResponse.ok(new BudgetResponse(budgetService.getBudget(memberId))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BudgetResponse>> saveBudget(
            Authentication authentication,
            @RequestBody BudgetDto request
    ) {
        Long memberId = Long.valueOf(authentication.getName());

        return ResponseEntity.ok(ApiResponse.ok(new BudgetResponse(budgetService.saveBudget(memberId, request))));
    }
}
