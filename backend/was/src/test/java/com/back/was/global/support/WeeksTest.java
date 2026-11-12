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
 *
 * <p>주 시작이 월요일인 것({@code Weeks.FIRST_DAY_OF_WEEK})은 <b>로케일에 맡기지 않은
 * 결정</b>이다. {@code WeekFields.of(Locale.getDefault())} 를 쓰면 "이번 주"가 서버 로케일에
 * 달리고, 그러면 넛지 판정 구간·주간 분석 막대·챌린지 기간이 환경마다 달라진다.
 *
 * <p>넛지 표본과 ANL-03 이 쓰던 구간 계산({@code elapsedWeekOf} · {@code elapsedWeeksAgo})과
 * 룰 기반 목표 제안이 쓰던 {@code fullWeeksBefore} 는 해당 기능들과 함께 빠졌다. 다시 만들 때
 * 고정해야 할 규칙:
 * <ul>
 *   <li><b>이번 주 분석 기간은 월요일 ~ 기준 날짜</b> (D → 11-02 ~ 11-04, 길이 k = 3)</li>
 *   <li><b>표본은 각 주의 "월요일 ~ k번째 날"</b> — 1주 전은 10-26 ~ 10-28, 4주 전은
 *       10-05 ~ 10-07. 요일이 무엇이든 표본 길이가 이번 주 경과 일수와 <b>같아야</b> 하고,
 *       그게 시연 6-1 의 "요일을 바꿔도 Z 가 9.32" 를 성립시키는 유일한 이유다</li>
 *   <li><b>직전 N주 평균은 주 전체(N × 7일)로</b> 끊는다 — 위의 k일 조각과 다르다.
 *       월요일에 k일 조각으로 평균을 내면 하루치가 나와 터무니없는 목표가 제안된다
 *       (AI_대체_문구 5장)</li>
 * </ul>
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
    @DisplayName("weekOf: 일곱 요일 어디서 불러도 같은 주를 가리키고 길이는 항상 7일이다")
    void weekOfIsStableAcrossTheWeek() {
        DateRange expected =
                new DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));

        for (int dayOfMonth = 2; dayOfMonth <= 8; dayOfMonth++) {
            LocalDate today = LocalDate.of(2026, 11, dayOfMonth);

            assertThat(Weeks.weekOf(today)).as("%s 의 주", today).isEqualTo(expected);
        }
    }
}
