package com.back.was.domain.budget.dto;

import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.SpendingFocus;

/**
 * BUDGET-01·02 응답 (명세 BUDGET-02 "BUDGET-01과 동일").
 *
 * <p>{@code spendingFocus} 는 미설정이 {@code null} 이고, 키 자체는 남아야 한다 —
 * {@code NON_NULL} 은 {@code ApiResponse} 의 {@code data}/{@code error} 에만 걸려 있고
 * 이 레코드에는 적용되지 않는다.
 */
public record BudgetResponse(
        String yearMonth,
        int monthlyBudget,
        int savingsGoal,
        String spendingFocus
) {
    public static BudgetResponse from(Budget budget) {
        SpendingFocus focus = budget.getSpendingFocus();
        return new BudgetResponse(
                budget.getYearMonth(),
                budget.getMonthlyBudget(),
                budget.getSavingsGoal(),
                focus == null ? null : focus.name());
    }
}
