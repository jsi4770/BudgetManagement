package com.back.was.domain.budget;

import com.back.was.domain.budget.dto.BudgetResponse;
import com.back.was.domain.budget.dto.SaveBudgetRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import com.back.was.global.support.YearMonths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 4장 예산(BUDGET). 두 엔드포인트 모두 인증 필요이고, 대상은 항상 토큰의 주인이라
 * 경로에 사용자 ID 가 없다.
 */
@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** BUDGET-01 월 예산·목표 조회 */
    @GetMapping("/{yearMonth}")
    public ApiResponse<BudgetResponse> getBudget(@AuthenticationPrincipal AuthUser authUser,
                                                 @PathVariable String yearMonth) {
        return ApiResponse.ok(budgetService.getBudget(authUser.id(), YearMonths.parse(yearMonth)));
    }

    /** BUDGET-02 월 예산·목표 저장 */
    @PutMapping("/{yearMonth}")
    public ApiResponse<BudgetResponse> saveBudget(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable String yearMonth,
                                                  @RequestBody @Valid SaveBudgetRequest request) {
        return ApiResponse.ok(budgetService.saveBudget(authUser.id(), YearMonths.parse(yearMonth), request));
    }
}
