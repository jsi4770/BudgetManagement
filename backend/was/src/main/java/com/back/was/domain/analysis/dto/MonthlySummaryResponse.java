package com.back.was.domain.analysis.dto;

import com.back.was.domain.transaction.AmountSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * ANL-01 월별 요약 응답 (홈 상단).
 *
 * <p>계산식은 명세 ANL-01 표 그대로다 — {@code balance = totalIncome − totalExpense},
 * {@code remainingBudget = budget − totalExpense},
 * {@code budgetUsageRate = totalExpense ÷ budget × 100}. 월세는 포함한다(ERD 4장: 홈
 * 총지출이 월간 탭 합계의 기준).
 *
 * <p><b>예산이 없을 때</b>(과거 월에 행이 없거나, 예산을 한 번도 설정하지 않음):
 * {@code budget}·{@code remainingBudget} 은 {@code null} 이고 키는 남는다. 명세가 정한
 * 것은 {@code remainingBudget: null} 뿐이고, 같은 상황의 {@code budget} 은 금액이 없으니
 * 0 원보다 {@code null} 이 정확하다 — 0 으로 내리면 "예산 0원으로 설정했음"과 구분되지
 * 않는다.
 *
 * <p><b>{@code budgetUsageRate} 는 그 경우에도 {@code 0.00} 이다</b>(2026-11-08 결정).
 * 명세에 규정이 없는 자리인데, 예산이 없거나 0원이면 나눗셈이 성립하지 않는다 —
 * {@code null} 이면 프론트의 진행률 바가 NaN 이 되고, 그대로 나누면 무한이 된다. 사용률만
 * 0 으로 떨어뜨리면 "쓸 수 있는 예산이 없다"가 화면에서 0% 로 표현된다. 예산이 0원으로
 * <i>설정된</i> 경우도 같은 값인데, 그 둘은 {@code budget} 필드({@code null} vs {@code 0})로
 * 구분된다.
 */
public record MonthlySummaryResponse(
        String yearMonth,
        int totalIncome,
        int totalExpense,
        int balance,
        Integer budget,
        Integer remainingBudget,
        BigDecimal budgetUsageRate
) {
    /** CLAUDE.md: 비율은 {@code BigDecimal} scale 2. */
    private static final int RATE_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 예산이 없거나 0원일 때의 사용률. {@code 0} 이 아니라 {@code 0.00} 으로 내보낸다. */
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(RATE_SCALE);

    /**
     * @param monthlyBudget 그 달의 예산. <b>{@code null} 이면 "예산 없음"</b>
     *                      (자동 복사까지 시도한 뒤의 결과여야 한다 — 서버_내부_처리 5장)
     */
    public static MonthlySummaryResponse of(YearMonth yearMonth, AmountSummary summary,
                                            Integer monthlyBudget) {
        int totalExpense = toAmount(summary.expense());

        return new MonthlySummaryResponse(
                yearMonth.toString(),
                toAmount(summary.income()),
                totalExpense,
                toAmount(summary.balance()),
                monthlyBudget,
                monthlyBudget == null ? null : monthlyBudget - totalExpense,
                usageRate(totalExpense, monthlyBudget));
    }

    /** 초과 지출이면 100 을 넘고, {@code remainingBudget} 은 음수가 된다 — 둘 다 정상 응답이다. */
    private static BigDecimal usageRate(int totalExpense, Integer monthlyBudget) {
        if (monthlyBudget == null || monthlyBudget == 0) {
            return ZERO_RATE;
        }
        return BigDecimal.valueOf(totalExpense)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(monthlyBudget), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * JPQL {@code sum()} 의 {@code Long} 을 명세의 int(원)로 좁힌다.
     * {@code CalendarResponse.Day} 와 같은 이유로 {@link Math#toIntExact} 를 쓴다 — 한 달
     * 합계가 int 범위를 넘는 건 명세의 금액 타입 자체를 벗어난 입력이고, 틀린 숫자를
     * 응답하는 것보다 500 으로 드러나는 편이 낫다.
     */
    private static int toAmount(long sum) {
        return Math.toIntExact(sum);
    }
}
