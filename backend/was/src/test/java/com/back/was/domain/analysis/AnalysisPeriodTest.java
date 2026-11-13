package com.back.was.domain.analysis;

import com.back.was.global.support.DateRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ANL-03 의 {@code WEEK}/{@code MONTH} 차이 (명세 ANL-03 표).
 *
 * <p>기준일은 docs/시연_데이터.md 의 D = 2026-11-04(수) — 주 표본은 직전 4주의 월~수,
 * 월 표본은 8·9·10월의 1~4일이다.
 */
class AnalysisPeriodTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 4);

    @Nested
    @DisplayName("WEEK (직전 4주)")
    class Week {

        @Test
        @DisplayName("분석 기간은 그 주 월요일 ~ 기준일")
        void analysisRange() {
            assertThat(AnalysisPeriod.WEEK.analysisRange(D))
                    .isEqualTo(new DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)));
        }

        @Test
        @DisplayName("표본은 4개, 각 주의 월요일 ~ 같은 요일")
        void sampleRanges() {
            assertThat(AnalysisPeriod.WEEK.sampleCount()).isEqualTo(4);
            assertThat(AnalysisPeriod.WEEK.sampleRange(D, 1))
                    .isEqualTo(new DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28)));
            assertThat(AnalysisPeriod.WEEK.sampleRange(D, 4))
                    .isEqualTo(new DateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)));
        }

        @Test
        @DisplayName("데이터 충분성 기준일은 표본 첫 주의 월요일 (ERD 6장 '가입일이 표본 첫 주 월요일 이후')")
        void oldestSampleStart() {
            assertThat(AnalysisPeriod.WEEK.oldestSampleStart(D)).isEqualTo(LocalDate.of(2026, 10, 5));
        }

        @Test
        @DisplayName("월세는 제외하고, 이상 판정은 한다")
        void rentAndAnomalyFlags() {
            assertThat(AnalysisPeriod.WEEK.isRentExcluded()).isTrue();
            assertThat(AnalysisPeriod.WEEK.isAnomalyDetected()).isTrue();
        }
    }

    @Nested
    @DisplayName("MONTH (직전 3개월)")
    class Month {

        @Test
        @DisplayName("분석 기간은 1일 ~ 기준일")
        void analysisRange() {
            assertThat(AnalysisPeriod.MONTH.analysisRange(D))
                    .isEqualTo(new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)));
        }

        @Test
        @DisplayName("시연 6-3: 표본은 3개, 8·9·10월의 1~4일")
        void sampleRanges() {
            assertThat(AnalysisPeriod.MONTH.sampleCount()).isEqualTo(3);
            assertThat(AnalysisPeriod.MONTH.sampleRange(D, 1))
                    .isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4)));
            assertThat(AnalysisPeriod.MONTH.sampleRange(D, 3))
                    .isEqualTo(new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4)));
        }

        @Test
        @DisplayName("데이터 충분성 기준일은 직전 3개월의 첫날 (명세 ANL-03)")
        void oldestSampleStart() {
            assertThat(AnalysisPeriod.MONTH.oldestSampleStart(D)).isEqualTo(LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("월세를 항목에서 빼지 않고(총지출에 포함), 이상 판정도 하지 않는다")
        void rentAndAnomalyFlags() {
            assertThat(AnalysisPeriod.MONTH.isRentExcluded()).isFalse();
            assertThat(AnalysisPeriod.MONTH.isAnomalyDetected()).isFalse();
        }
    }
}
