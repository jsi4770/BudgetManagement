package com.back.was.domain.budget;

import com.back.was.domain.budget.dto.BudgetResponse;
import com.back.was.domain.budget.dto.SaveBudgetRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import com.back.was.global.support.YearMonths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. 예산 (BUDGET)", description = """
        월 예산·저축 목표·소비 관리 목표. `{yearMonth}` 는 `YYYY-MM` 형식입니다.""")
@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @Operation(summary = "BUDGET-01 월 예산·목표 조회", description = """
            **조회인데 행을 만들 수 있습니다.** 이번 달 예산이 없으면 *가장 최근 달*의 예산을 복사해 만듭니다
            (지난 달이 아니라 "가장 최근" — 오래 쉰 사용자는 지난 달 행이 없을 수 있어서).

            자동 생성은 **이번 달에만** 합니다. 지난 달에 행이 없으면 그냥 404 입니다 —
            늦게 만든 행이 과거의 사용률을 바꿔 버리기 때문입니다.

            **실패**: `400 VALIDATION_ERROR`(형식이 `YYYY-MM` 이 아님),
            `404 NOT_FOUND`(복사할 예산이 아예 없음 → 프론트는 초기 설정 화면으로)""")
    @GetMapping("/{yearMonth}")
    public ApiResponse<BudgetResponse> getBudget(@AuthenticationPrincipal AuthUser authUser,
                                                 @PathVariable String yearMonth) {
        return ApiResponse.ok(budgetService.getBudget(authUser.id(), YearMonths.parse(yearMonth)));
    }

    @Operation(summary = "BUDGET-02 월 예산·목표 저장", description = """
            **PUT 이라 보내지 않은 필드는 비워집니다.** `spendingFocus` 를 생략하면 "목표 없음"이 되고,
            PATCH 처럼 기존 값이 유지되지 않습니다.

            `savingsGoal` 만 기본값이 있습니다(0). 경로의 달만 바뀌므로 과거 달은 건드리지 않습니다.

            **실패**: `400 VALIDATION_ERROR`""")
    @PutMapping("/{yearMonth}")
    public ApiResponse<BudgetResponse> saveBudget(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable String yearMonth,
                                                  @RequestBody @Valid SaveBudgetRequest request) {
        return ApiResponse.ok(budgetService.saveBudget(authUser.id(), YearMonths.parse(yearMonth), request));
    }
}
