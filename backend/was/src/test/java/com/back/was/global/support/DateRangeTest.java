package com.back.was.global.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 분석 기간 구간 — 양끝 포함, 역순 금지. */
class DateRangeTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);

    @Test
    @DisplayName("contains 는 양끝을 포함한다")
    void containsBothEnds() {
        DateRange range = new DateRange(MONDAY, WEDNESDAY);

        assertThat(range.contains(MONDAY)).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 11, 3))).isTrue();
        assertThat(range.contains(WEDNESDAY)).isTrue();
        assertThat(range.contains(MONDAY.minusDays(1))).isFalse();
        assertThat(range.contains(WEDNESDAY.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("하루짜리 구간도 그 날짜를 포함한다 — 월요일에 판정하면 k = 1 이다")
    void singleDayRange() {
        assertThat(new DateRange(MONDAY, MONDAY).contains(MONDAY)).isTrue();
    }

    @Test
    @DisplayName("종료가 시작보다 앞서면 만들 수 없다 — (from, to) 를 뒤집어 넘기는 실수를 생성 시점에 막는다")
    void rejectsReversedRange() {
        assertThatThrownBy(() -> new DateRange(WEDNESDAY, MONDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-11-04");
    }

    @Test
    @DisplayName("null 날짜는 받지 않는다")
    void rejectsNull() {
        assertThatThrownBy(() -> new DateRange(null, WEDNESDAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DateRange(MONDAY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
