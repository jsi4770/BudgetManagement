package com.back.was.global.support;

import java.time.YearMonth;

/**
 * 월 단위 분석 기간. {@link Weeks} 의 월 버전이고, 쓰이는 곳은 ANL-01(월별 요약)과
 * ANL-03 {@code MONTH}(직전 3개월 비교)다.
 *
 * <p>{@link YearMonths} 와 역할이 다르다 — 그쪽은 {@code YYYY-MM} 문자열의 파싱·검증,
 * 여기는 연월을 날짜 구간으로 펼치는 일이다.
 */
public final class Months {

    private Months() {
    }

    /** 해당 월 1일 ~ 말일. ANL-01 의 집계 범위. */
    public static DateRange monthOf(YearMonth yearMonth) {
        return new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }
}
