package com.back.was.global.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월 단위 분석 기간 (ANL-01).
 *
 * <p>원래 ANL-03 {@code MONTH} 의 표본 구간 계산({@code elapsedMonthOf} ·
 * {@code elapsedMonthsAgo})도 여기서 검증했다 — 그 두 메서드는 ANL-03 과 함께 빠졌다.
 * 다시 만들 때 고정해야 할 규칙은 세 가지이고 전부 명세·시연 문서에 있다:
 * <ul>
 *   <li>이번 달 분석 기간은 <b>1일 ~ 기준 날짜</b> (D = 2026-11-04 → 11-01 ~ 11-04)</li>
 *   <li>직전 3개월 표본은 <b>각 달의 1일 ~ 같은 날짜</b> (시연 6-3 → 8·9·10월의 1~4일)</li>
 *   <li><b>같은 날짜가 없는 달은 그 달 전체</b> (명세 ANL-03) — 3/31 의 1개월 전은
 *       2/1 ~ 2/28, 윤년이면 2/29. 해를 넘어가도 같은 날짜를 찾는다</li>
 * </ul>
 */
class MonthsTest {

    @Test
    @DisplayName("monthOf: ANL-01 집계 범위는 1일 ~ 말일")
    void monthOfCoversWholeMonth() {
        assertThat(Months.monthOf(YearMonth.of(2026, 11)))
                .isEqualTo(new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        assertThat(Months.monthOf(YearMonth.of(2026, 2)).end())
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("monthOf: 윤년 2월은 29일까지다")
    void leapYearFebruary() {
        assertThat(Months.monthOf(YearMonth.of(2028, 2)).end())
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
