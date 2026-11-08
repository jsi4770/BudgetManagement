package com.back.was.global.support;

import java.time.LocalDate;
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

    /** 1일 ~ 주어진 날짜 (= 이번 달 경과분). ANL-03 {@code MONTH} 의 분석 기간. */
    public static DateRange elapsedMonthOf(LocalDate date) {
        return new DateRange(date.withDayOfMonth(1), date);
    }

    /**
     * {@code monthsAgo} 개월 전의 "1일 ~ 같은 날짜" — ANL-03 {@code MONTH} 의 표본 구간.
     *
     * <p>명세 ANL-03: <b>같은 날짜가 없는 달은 그 달 전체</b>. 3월 31일 기준의 1개월 전은
     * 2월 31일이 없으므로 2월 1일 ~ 28일이 된다. {@code LocalDate.minusMonths} 는 말일로
     * 잘라 주지만("2026-03-31 → 2026-02-28") 그 결과가 곧 말일이므로 명세의 규칙과 같은
     * 구간이 나온다 — 그래도 의도가 보이게 말일을 직접 계산한다.
     *
     * @param monthsAgo 1 이면 지난 달, 3 이면 3개월 전
     */
    public static DateRange elapsedMonthsAgo(LocalDate date, int monthsAgo) {
        YearMonth target = YearMonth.from(date).minusMonths(monthsAgo);
        int lastDay = Math.min(date.getDayOfMonth(), target.lengthOfMonth());
        return new DateRange(target.atDay(1), target.atDay(lastDay));
    }
}
