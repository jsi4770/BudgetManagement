package com.back.was.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** 날짜 공통: 모든 날짜는 KST, 주는 월요일 시작 (명세 2) 데이터 형식) */
public final class TimeUtil {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String WEEKDAYS = "월화수목금토일";

    private TimeUtil() {}

    /** 서버 기준 오늘 (KST) */
    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** 그 날이 속한 주의 월요일 */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** "월" ~ "일" */
    public static String koreanDayOfWeek(LocalDate date) {
        int i = date.getDayOfWeek().getValue() - 1;
        return WEEKDAYS.substring(i, i + 1);
    }
}
