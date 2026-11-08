package com.back.was.domain.analysis.dto;

import com.back.was.global.support.DateRange;

import java.time.LocalDate;
import java.util.List;

/**
 * ANL-02 응답 (소비 분석 – 주간 막대).
 *
 * <p>요청한 주 수만큼 <b>항상</b> 채워지고 오래된 주부터 정렬된다(명세 ANL-02 예시 순서).
 * 거래가 없는 주도 0원 막대로 들어간다 — TX-03 달력이 "거래가 있는 날짜만" 주는 것과
 * 반대인데, 차트의 x축은 빈 주에도 칸이 있어야 하기 때문이다.
 *
 * <p>금액은 <b>월세 제외</b>다 (ERD 4장 · 화면 "이번 주 지출(월세 제외)").
 */
public record WeeklyTrendResponse(
        List<Week> weeks
) {
    /**
     * 막대 하나.
     *
     * <p>{@code weekStart}·{@code weekEnd} 는 <b>월요일 ~ 일요일</b>이고, 이번 주 막대도
     * 그렇다 (명세 ANL-02 예시: 오늘이 11-04 여도 {@code weekEnd} 는 11-08). 즉 이번 주는
     * 라벨 기간과 합계 기간이 다르다 — {@code totalExpense} 는 명세 본문대로 <b>월요일 ~
     * 오늘</b>까지만 더한 값이다(결정 2026-11-08). 라벨은 주 전체를 보여 주는 쪽이 막대
     * 차트의 x축으로 자연스럽고, 합계에 아직 오지 않은 날을 넣을 방법은 없다.
     */
    public record Week(
            LocalDate weekStart,
            LocalDate weekEnd,
            int totalExpense
    ) {
        /** JPQL {@code sum()} 의 {@code Long} 을 명세의 int(원)로 좁힌다 ({@code MonthlySummaryResponse} 와 같은 이유). */
        public static Week of(DateRange week, long totalExpense) {
            return new Week(week.start(), week.end(), Math.toIntExact(totalExpense));
        }
    }
}
