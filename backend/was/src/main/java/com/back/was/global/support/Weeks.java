package com.back.was.global.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 서버_내부_처리 공통 전제: 주(week)는 <b>월요일 시작</b>.
 *
 * <p>{@code WeekFields.of(Locale.getDefault())} 로 주 경계를 잡으면 로케일에 따라 일요일
 * 시작이 될 수 있다 — 넛지 판정(서버_내부_처리 3장)과 ANL-02·03 의 "이번 주"가 서버
 * 로케일에 좌우되면 안 되므로 월요일을 상수로 박는다. "오늘"의 기준은
 * {@link KstTime#today()} 이고, 이 클래스는 받은 날짜만 가지고 계산한다.
 *
 * <p>핵심은 {@link #elapsedWeekOf(LocalDate)} 와 {@link #elapsedWeeksAgo(LocalDate, int)}
 * 의 짝이다. 표본을 "직전 4주 전체"가 아니라 <b>각 주의 월요일 ~ k번째 날</b>로 자르기
 * 때문에(ERD 6장), 수요일에 판정해도 수요일까지끼리만 비교된다 — 시연_데이터 6-1 의
 * "요일을 바꿔 계산해도 배달 Z 는 9.32" 가 성립하는 이유다.
 */
public final class Weeks {

    /** ISO-8601 과 같은 월요일 시작 (서버_내부_처리 공통 전제). */
    public static final DayOfWeek FIRST_DAY_OF_WEEK = DayOfWeek.MONDAY;

    private Weeks() {
    }

    /** 그 날짜가 속한 주의 월요일 (월요일을 넣으면 그 날짜 그대로). */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK));
    }

    /**
     * 그 주의 경과 일수 k (월 = 1 … 일 = 7).
     *
     * <p>{@link DayOfWeek#getValue()} 가 월요일을 1 로 세는 것이 곧 이 정의다 — 주 시작이
     * 월요일이라서 둘이 맞는다.
     */
    public static int elapsedDays(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    /** 월요일 ~ 일요일 한 주 전체. ANL-02 막대의 {@code weekStart}·{@code weekEnd} 라벨. */
    public static DateRange weekOf(LocalDate date) {
        LocalDate monday = mondayOf(date);
        return new DateRange(monday, monday.plusDays(6));
    }
}
