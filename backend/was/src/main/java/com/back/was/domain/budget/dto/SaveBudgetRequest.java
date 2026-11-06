package com.back.was.domain.budget.dto;

import com.back.was.domain.budget.SpendingFocus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * BUDGET-02 요청. 규칙은 명세 BUDGET-02 표를 그대로 옮겼다.
 *
 * <p>{@code monthlyBudget} 만 필수다. {@code savingsGoal} 은 선택이고 미전송 시 기본값 0
 * 이라 {@code int} 가 아니라 {@code Integer} 로 받는다 — {@code int} 면 미전송과 0 을
 * 구분할 수 없고 {@code @NotNull} 도 무의미해진다.
 *
 * <p>{@code spendingFocus} 는 {@code null} 이 허용값("목표 없음")이다. 열거형에 없는
 * 문자열은 Jackson 역직렬화가 실패해 {@code HttpMessageNotReadableException} → 400
 * {@code VALIDATION_ERROR} 로 떨어진다.
 */
public record SaveBudgetRequest(

        @NotNull
        @Min(0)
        Integer monthlyBudget,

        @Min(0)
        Integer savingsGoal,

        SpendingFocus spendingFocus
) {
    /** 명세 BUDGET-02: {@code savingsGoal} 기본값 0. */
    public int savingsGoalOrZero() {
        return savingsGoal == null ? 0 : savingsGoal;
    }
}
