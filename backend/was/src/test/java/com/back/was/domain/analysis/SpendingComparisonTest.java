package com.back.was.domain.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Z-score 계산 단위 테스트 (CLAUDE.md: 넛지 판정은 단위 테스트 필수).
 *
 * <p>기대값은 전부 docs/시연_데이터.md 6장 — D = 2026-11-04(수), k = 3 기준이다. 이 숫자가
 * 틀어지면 넛지 카드와 ANL-03 화면이 같이 틀어진다.
 */
class SpendingComparisonTest {

    @Nested
    @DisplayName("시연 6-1 주간 (직전 4주 표본)")
    class WeeklySamples {

        @Test
        @DisplayName("배달: 101,000 vs 55/62/58/65천 → 평균 60,000 · 표준편차 4,397 · Z 9.32 · +68.33% → 넛지 생성")
        void deliverySpike() {
            SpendingComparison comparison = SpendingComparison.of(
                    101_000, List.of(55_000, 62_000, 58_000, 65_000));

            assertThat(comparison.average()).isEqualTo(60_000);
            assertThat(comparison.standardDeviation().setScale(0, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("4397");
            assertThat(comparison.zScore()).isEqualByComparingTo("9.32");
            assertThat(comparison.growthRate()).isEqualByComparingTo("68.33");
            assertThat(comparison.difference()).isEqualTo(41_000);
            assertThat(comparison.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("NORMAL 시나리오 배달 58,000 → Z −0.45 → 넛지 없음")
        void normalScenarioIsNotAnomaly() {
            SpendingComparison comparison = SpendingComparison.of(
                    58_000, List.of(55_000, 62_000, 58_000, 65_000));

            assertThat(comparison.zScore()).isEqualByComparingTo("-0.45");
            assertThat(comparison.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("식비: 표본이 전부 17,500 → 표준편차 0 이라 Z 는 null (판정 생략), 증가율은 계산된다")
        void zeroDeviationSkipsJudgement() {
            SpendingComparison comparison = SpendingComparison.of(
                    9_000, List.of(17_500, 17_500, 17_500, 17_500));

            assertThat(comparison.average()).isEqualTo(17_500);
            assertThat(comparison.standardDeviation()).isEqualByComparingTo("0");
            assertThat(comparison.zScore()).isNull();
            assertThat(comparison.growthRate()).isEqualByComparingTo("-48.57");
            assertThat(comparison.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("기타: 표본이 전부 0원 → 평균 0 이라 증가율도 null (0 나눗셈 없음)")
        void allZeroSamples() {
            SpendingComparison comparison = SpendingComparison.of(0, List.of(0, 0, 0, 0));

            assertThat(comparison.average()).isZero();
            assertThat(comparison.zScore()).isNull();
            assertThat(comparison.growthRate()).isNull();
            assertThat(comparison.isAnomaly()).isFalse();
        }
    }

    @Nested
    @DisplayName("시연 6-3 월간 (직전 3개월 표본)")
    class MonthlySamples {

        @Test
        @DisplayName("배달: 101,000 vs 27/33/0천 → 평균 20,000 · +405.00%")
        void delivery() {
            SpendingComparison comparison = SpendingComparison.of(
                    101_000, List.of(27_000, 33_000, 0));

            assertThat(comparison.average()).isEqualTo(20_000);
            assertThat(comparison.growthRate()).isEqualByComparingTo("405.00");
        }

        @Test
        @DisplayName("교통: 6,200/12,400/6,200 평균은 8,266.67 → 8,267 로 반올림, 증가율 −80.04%")
        void transportAverageIsRoundedToWon() {
            SpendingComparison comparison = SpendingComparison.of(1_650, List.of(6_200, 12_400, 6_200));

            assertThat(comparison.average()).isEqualTo(8_267);
            assertThat(comparison.growthRate()).isEqualByComparingTo("-80.04");
        }

        @Test
        @DisplayName("기타: 15,000/0/0 → 평균 5,000, 이번 달 0원이면 −100.00%")
        void spentNothingThisMonth() {
            SpendingComparison comparison = SpendingComparison.of(0, List.of(15_000, 0, 0));

            assertThat(comparison.average()).isEqualTo(5_000);
            assertThat(comparison.growthRate()).isEqualByComparingTo("-100.00");
        }
    }

    @Nested
    @DisplayName("이상 판정 조건 (Z ≥ 2 AND 차이 ≥ 30,000원)")
    class AnomalyRule {

        @Test
        @DisplayName("Z 는 충분한데 차이가 30,000원 미만이면 이상이 아니다 — 평소 1,250원이 3,000원 된 경우")
        void amountFloorBlocksSmallSpikes() {
            SpendingComparison comparison = SpendingComparison.of(
                    3_000, List.of(1_000, 1_000, 1_000, 2_000));

            assertThat(comparison.zScore()).isGreaterThanOrEqualTo(SpendingComparison.ANOMALY_Z_SCORE);
            assertThat(comparison.difference()).isLessThan(SpendingComparison.ANOMALY_AMOUNT_DIFF);
            assertThat(comparison.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("차이는 충분한데 Z 가 2 미만이면 이상이 아니다 — 들쭉날쭉한 카테고리")
        void zScoreFloorBlocksNoisyCategories() {
            SpendingComparison comparison = SpendingComparison.of(
                    100_000, List.of(0, 150_000, 0, 130_000));

            assertThat(comparison.difference()).isGreaterThanOrEqualTo(SpendingComparison.ANOMALY_AMOUNT_DIFF);
            assertThat(comparison.zScore()).isLessThan(SpendingComparison.ANOMALY_Z_SCORE);
            assertThat(comparison.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("Z 가 정확히 2, 차이가 정확히 30,000원이면 이상이다 (경계 포함)")
        void boundaryIsInclusive() {
            // 표본 0/0/0/30,000 → 평균 7,500 · 표본표준편차 정확히 15,000
            SpendingComparison comparison = SpendingComparison.of(37_500, List.of(0, 0, 0, 30_000));

            assertThat(comparison.average()).isEqualTo(7_500);
            assertThat(comparison.standardDeviation()).isEqualByComparingTo("15000");
            assertThat(comparison.zScore()).isEqualByComparingTo("2.00");
            assertThat(comparison.difference()).isEqualTo(30_000);
            assertThat(comparison.isAnomaly()).isTrue();
        }
    }

    @Test
    @DisplayName("표본이 2개 미만이면 표본표준편차를 낼 수 없다 (n − 1)")
    void rejectsTooFewSamples() {
        assertThatThrownBy(() -> SpendingComparison.of(1_000, List.of(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpendingComparison.of(1_000, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpendingComparison.of(1_000, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("표본 목록은 복사된다 — 넘긴 리스트를 나중에 바꿔도 결과가 변하지 않는다")
    void samplesAreCopied() {
        List<Integer> mutable = new ArrayList<>(List.of(55_000, 62_000, 58_000, 65_000));
        SpendingComparison comparison = SpendingComparison.of(101_000, mutable);

        mutable.set(0, 999_999);

        assertThat(comparison.samples()).containsExactly(55_000, 62_000, 58_000, 65_000);
        assertThat(comparison.average()).isEqualTo(60_000);
    }

    @Test
    @DisplayName("비율은 명세 0장 2)대로 scale 2 로 고정된다 — 0.00 도 소수점 두 자리다")
    void ratesKeepScaleTwo() {
        SpendingComparison comparison = SpendingComparison.of(4_500, List.of(4_500, 4_500, 4_500, 4_500));

        assertThat(comparison.growthRate()).isEqualTo(new BigDecimal("0.00"));
        assertThat(comparison.growthRate().scale()).isEqualTo(2);
    }
}
