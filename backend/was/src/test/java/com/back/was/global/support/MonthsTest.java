package com.back.was.global.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월 단위 분석 기간 (ANL-01 · ANL-03 {@code MONTH}).
 *
 * <p>기대값은 docs/시연_데이터.md 6-3 — D = 2026-11-04 기준 비교 대상은 8·9·10월의 1~4일.
 */
class MonthsTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 4);

    @Test
    @DisplayName("monthOf: ANL-01 집계 범위는 1일 ~ 말일")
    void monthOfCoversWholeMonth() {
        assertThat(Months.monthOf(YearMonth.of(2026, 11)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        assertThat(Months.monthOf(YearMonth.of(2026, 2)).end())
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("elapsedMonthOf: 이번 달 분석 기간은 1일 ~ 기준 날짜")
    void elapsedMonthStopsAtGivenDate() {
        assertThat(Months.elapsedMonthOf(D))
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)));
    }

    @Test
    @DisplayName("시연 6-3: 직전 3개월 표본은 8·9·10월의 1~4일")
    void sampleRangesAreTheSameDaysOfPreviousMonths() {
        assertThat(Months.elapsedMonthsAgo(D, 1))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4)));
        assertThat(Months.elapsedMonthsAgo(D, 2))
                .isEqualTo(new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)));
        assertThat(Months.elapsedMonthsAgo(D, 3))
                .isEqualTo(new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4)));
    }

    @Test
    @DisplayName("명세 ANL-03: 같은 날짜가 없는 달은 그 달 전체 — 3/31 의 1개월 전은 2/1 ~ 2/28")
    void missingDayFallsBackToWholeMonth() {
        assertThat(Months.elapsedMonthsAgo(LocalDate.of(2026, 3, 31), 1))
                .isEqualTo(new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        assertThat(Months.elapsedMonthsAgo(LocalDate.of(2026, 5, 31), 1))
                .isEqualTo(new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));
    }

    @Test
    @DisplayName("윤년 2월은 29일까지다")
    void leapYearFebruary() {
        assertThat(Months.elapsedMonthsAgo(LocalDate.of(2028, 3, 31), 1))
                .isEqualTo(new DateRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
    }

    @Test
    @DisplayName("해를 넘어가도 같은 날짜를 찾는다 — 1월 기준 3개월 전은 작년 10월")
    void crossesTheYearBoundary() {
        assertThat(Months.elapsedMonthsAgo(LocalDate.of(2027, 1, 15), 3))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)));
    }
}
