package com.back.was.domain.analysis;

import com.back.was.domain.analysis.dto.MonthlySummaryResponse;
import com.back.was.domain.transaction.AmountSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ANL-01 계산식 단위 테스트 (명세 ANL-01 표).
 *
 * <p>특히 <b>예산이 없거나 0원일 때 사용률이 0.00</b> 인 규칙이 여기 있다 — 명세에 없는
 * 자리라 결정으로 메운 부분이고(2026-11-08), 0 나눗셈·NaN 이 프론트까지 새어 나가지 않는
 * 유일한 방어선이다.
 */
class MonthlySummaryResponseTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private static MonthlySummaryResponse of(long income, long expense, Integer budget) {
        return MonthlySummaryResponse.of(NOVEMBER, new AmountSummary(income, expense), budget);
    }

    @Test
    @DisplayName("명세 ANL-01 예시: 수입 2,400,000 · 지출 666,150 · 예산 1,200,000 → 잔액 1,733,850 · 남은 533,850 · 55.51%")
    void matchesTheSpecExample() {
        MonthlySummaryResponse response = of(2_400_000, 666_150, 1_200_000);

        assertThat(response).isEqualTo(new MonthlySummaryResponse(
                "2026-11", 2_400_000, 666_150, 1_733_850, 1_200_000, 533_850,
                new BigDecimal("55.51")));
    }

    @Test
    @DisplayName("예산 없음: budget·remainingBudget 은 null, 사용률은 0.00 (무한·NaN 방지)")
    void noBudgetGivesZeroRate() {
        MonthlySummaryResponse response = of(2_400_000, 666_150, null);

        assertThat(response.budget()).isNull();
        assertThat(response.remainingBudget()).isNull();
        assertThat(response.budgetUsageRate()).isEqualByComparingTo("0.00");
        assertThat(response.totalExpense()).isEqualTo(666_150);
    }

    @Test
    @DisplayName("예산 0원으로 설정: 사용률도 0.00, 단 budget 은 0 이라 '예산 없음'과 구분된다")
    void zeroBudgetGivesZeroRate() {
        MonthlySummaryResponse response = of(0, 50_000, 0);

        assertThat(response.budget()).isZero();
        assertThat(response.remainingBudget()).isEqualTo(-50_000);
        assertThat(response.budgetUsageRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("예산 초과: remainingBudget 은 음수, 사용률은 100 을 넘는다 (둘 다 정상 응답)")
    void overspending() {
        MonthlySummaryResponse response = of(0, 1_500_000, 1_200_000);

        assertThat(response.remainingBudget()).isEqualTo(-300_000);
        assertThat(response.budgetUsageRate()).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("거래가 없는 달도 0 원이다 — 가입 직후 홈 화면이 열려야 한다")
    void emptyMonth() {
        MonthlySummaryResponse response = of(0, 0, 1_200_000);

        assertThat(response.totalIncome()).isZero();
        assertThat(response.totalExpense()).isZero();
        assertThat(response.balance()).isZero();
        assertThat(response.remainingBudget()).isEqualTo(1_200_000);
        assertThat(response.budgetUsageRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("수입보다 지출이 많으면 balance 는 음수다")
    void negativeBalance() {
        assertThat(of(100_000, 150_000, 1_200_000).balance()).isEqualTo(-50_000);
    }

    @Test
    @DisplayName("사용률은 scale 2 로 반올림된다 (명세 0장 2) 비율)")
    void rateKeepsScaleTwo() {
        // 333,333 ÷ 1,000,000 × 100 = 33.3333 → 33.33
        assertThat(of(0, 333_333, 1_000_000).budgetUsageRate())
                .isEqualTo(new BigDecimal("33.33"));
        // 0% 도 "0" 이 아니라 "0.00" 으로 나간다
        assertThat(of(0, 0, 1_200_000).budgetUsageRate().scale()).isEqualTo(2);
    }
}
