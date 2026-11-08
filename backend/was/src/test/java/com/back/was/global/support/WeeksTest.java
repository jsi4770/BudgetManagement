package com.back.was.global.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월요일 시작 주 경계 (서버_내부_처리 공통 전제).
 *
 * <p>기대값은 docs/시연_데이터.md 용어 표 — D = 2026-11-04(수), M0 = 2026-11-02, k = 3,
 * 1주 전 = 10-26 ~ 11-01.
 */
class WeeksTest {

    /** 시연일 D (수요일). */
    private static final LocalDate D = LocalDate.of(2026, 11, 4);

    @Test
    @DisplayName("mondayOf: 주중 날짜는 그 주 월요일로, 월요일은 그대로")
    void mondayOfWeekday() {
        assertThat(Weeks.mondayOf(D)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(Weeks.mondayOf(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    @DisplayName("mondayOf: 일요일은 '다음 주 월요일'이 아니라 그 주 월요일 — 일요일 시작 로케일이면 틀린다")
    void sundayBelongsToTheWeekThatStarted() {
        assertThat(Weeks.mondayOf(LocalDate.of(2026, 11, 8))).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(Weeks.mondayOf(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    @DisplayName("elapsedDays: k 는 월 = 1 … 일 = 7")
    void elapsedDaysCountsFromMonday() {
        assertThat(Weeks.elapsedDays(LocalDate.of(2026, 11, 2))).isEqualTo(1);
        assertThat(Weeks.elapsedDays(D)).isEqualTo(3);
        assertThat(Weeks.elapsedDays(LocalDate.of(2026, 11, 8))).isEqualTo(7);
    }

    @Test
    @DisplayName("elapsedWeekOf: 이번 주 분석 기간은 월요일 ~ 오늘 (명세 ANL-02·ERD 6장)")
    void elapsedWeekStopsAtGivenDate() {
        assertThat(Weeks.elapsedWeekOf(D))
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)));
    }

    @Test
    @DisplayName("weekOf: 막대 라벨용 월요일 ~ 일요일 전체 주")
    void weekOfCoversMondayToSunday() {
        assertThat(Weeks.weekOf(D))
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)));
    }

    @Test
    @DisplayName("시연 6-2: 3주 전 ~ 이번 주 막대 구간이 10-12 ~ 10-18 … 11-02 ~ 11-08 이다")
    void weeklyBarRanges() {
        assertThat(Weeks.weekOf(D.minusWeeks(3)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)));
        assertThat(Weeks.weekOf(D.minusWeeks(2)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));
        assertThat(Weeks.weekOf(D.minusWeeks(1)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)));
    }

    @Test
    @DisplayName("elapsedWeeksAgo: 표본은 각 주의 '월요일 ~ k번째 날' — 1주 전은 10-26 ~ 10-28")
    void sampleRangesKeepTheSameElapsedDays() {
        assertThat(Weeks.elapsedWeeksAgo(D, 1))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28)));
        assertThat(Weeks.elapsedWeeksAgo(D, 4))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)));
    }

    @Test
    @DisplayName("요일이 무엇이든 표본 구간의 길이는 이번 주 경과 일수와 같다 (시연 6-1: 요일을 바꿔도 Z 동일)")
    void sampleRangesAlwaysHaveTheSameLengthAsThisWeek() {
        // 2026-11-02(월) ~ 11-08(일) 일곱 요일 전부
        for (int dayOfMonth = 2; dayOfMonth <= 8; dayOfMonth++) {
            LocalDate today = LocalDate.of(2026, 11, dayOfMonth);
            int elapsed = Weeks.elapsedDays(today);

            assertThat(lengthOf(Weeks.elapsedWeekOf(today)))
                    .as("%s 의 이번 주 경과 일수", today)
                    .isEqualTo(elapsed);
            for (int weeksAgo = 1; weeksAgo <= 4; weeksAgo++) {
                DateRange sample = Weeks.elapsedWeeksAgo(today, weeksAgo);
                assertThat(lengthOf(sample))
                        .as("%s 기준 %d주 전 표본 구간 길이", today, weeksAgo)
                        .isEqualTo(elapsed);
                assertThat(sample.start()).isEqualTo(Weeks.mondayOf(today).minusWeeks(weeksAgo));
            }
        }
    }

    private static int lengthOf(DateRange range) {
        return (int) (range.end().toEpochDay() - range.start().toEpochDay()) + 1;
    }
}
