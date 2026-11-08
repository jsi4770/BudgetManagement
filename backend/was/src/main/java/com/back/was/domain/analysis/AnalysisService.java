package com.back.was.domain.analysis;

import com.back.was.domain.analysis.dto.MonthlySummaryResponse;
import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.BudgetService;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.transaction.AmountSummary;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.Months;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * 명세 8장 소비 분석(ANALYSIS). <b>분석 결과를 저장하지 않는다</b> — 명세 8장 머리말대로
 * 조회 시점에 TRANSACTION 을 집계한다. 그래서 거래 후처리(넛지·챌린지)가 아직 없어도 이
 * 숫자는 항상 맞는다.
 *
 * <p>모든 집계 쿼리에 {@code userId} 가 들어가므로 남의 거래는 결과에 섞이지 않는다
 * (서버_내부_처리 공통 전제). 경로·쿼리에 사용자 ID 가 없어 범위를 바꿔 찔러볼 수단도 없다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;

    /**
     * ANL-01 월별 요약.
     *
     * <p><b>{@code readOnly} 가 아닌 이유</b>: 예산 자동 복사(서버_내부_처리 5장)가 INSERT
     * 를 할 수 있다. 명세 ANL-01 의 {@code remainingBudget} 설명이 "이번 달 예산 없으면 최근
     * 예산 복사 후 계산"이라 BUDGET-01 과 같은 규칙을 타야 하고, 그래서 규칙을 복제하지 않고
     * {@link BudgetService#findOrCopy} 를 호출한다.
     *
     * <p>예산을 찾지 못해도 404 가 아니다 — 명세 ANL-01 은 그 상황을
     * {@code remainingBudget: null} 로 표현한다. 404 는 BUDGET-01 쪽의 "초기 설정 화면으로
     * 보내라" 신호다.
     *
     * <p>거래가 한 건도 없는 달도 200 + 0 원이다. 홈 화면은 가입 직후에도 열려야 한다.
     */
    @Transactional
    public MonthlySummaryResponse getMonthlySummary(Long userId, YearMonth yearMonth) {
        DateRange month = Months.monthOf(yearMonth);

        AmountSummary summary = transactionRepository.sumAmounts(
                userId, month.start(), month.end(), CategoryType.INCOME, CategoryType.EXPENSE);

        Integer monthlyBudget = budgetService.findOrCopy(userId, yearMonth)
                .map(Budget::getMonthlyBudget)
                .orElse(null);

        return MonthlySummaryResponse.of(yearMonth, summary, monthlyBudget);
    }
}
