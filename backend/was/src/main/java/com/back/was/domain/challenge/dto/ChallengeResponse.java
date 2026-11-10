package com.back.was.domain.challenge.dto;

import com.back.was.domain.category.dto.CategoryRefResponse;
import com.back.was.domain.challenge.Challenge;
import com.back.was.global.support.KstTime;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 챌린지 한 건 — <b>CHL-01 의 배열 원소와 CHL-02 의 단일 객체가 같은 모양</b>이다
 * (명세 CHL-02: "CHL-01 항목과 같은 형식"). COACH-01·02 가 {@code CoachingResponse} 를
 * 공유하는 것과 같은 판단이다.
 *
 * <p>ERD 9장의 컬럼을 거의 그대로 옮기지만 두 가지가 다르다:
 *
 * <ul>
 *   <li><b>{@code coachingId} 가 없다.</b> 저장은 그 FK 로 사용자를 찾지만(ERD 9장) 화면이
 *       쓸 값이 아니다. 노출하면 남의 코칭 ID 를 추측할 재료가 되기도 한다.</li>
 *   <li><b>{@code daysLeft} 는 저장된 값이 아니다</b>(명세 CHL-01 "서버 계산, 미저장").
 *       {@link #daysLeftOf} 가 조회 시점마다 센다.</li>
 * </ul>
 *
 * <p>{@code category} 는 {@code {id, name}} 이다(명세 CHL-01 예시) — ANL-03·넛지·코칭 제안이
 * 쓰는 {@link CategoryRefResponse} 와 키가 같아 그 타입을 재사용한다. 챌린지의 카테고리는
 * 항상 지출이라({@code COACH-04} 가 검증) {@code type} 이 자명하다.
 *
 * <p>{@code goalAmount}·{@code goalCount} 는 {@code goalType} 에 따라 한쪽만 값이 있고
 * <b>두 키 모두 응답에 남는다</b>(명세 예시의 {@code "goalCount": null}).
 */
public record ChallengeResponse(
        Long id,
        String title,
        CategoryRefResponse category,
        LocalDate startDate,
        LocalDate endDate,
        String goalType,
        Integer goalAmount,
        Integer goalCount,
        int spendingAmount,
        int spendingCount,
        BigDecimal achievementRate,
        String status,
        long daysLeft
) {
    public static ChallengeResponse from(Challenge challenge) {
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getTitle(),
                CategoryRefResponse.from(challenge.getCategory()),
                challenge.getStartDate(),
                challenge.getEndDate(),
                challenge.getGoalType().name(),
                challenge.getGoalAmount(),
                challenge.getGoalCount(),
                challenge.getSpendingAmount(),
                challenge.getSpendingCount(),
                challenge.getAchievementRate(),
                challenge.getStatus().name(),
                daysLeftOf(challenge.getEndDate()));
    }

    /**
     * 종료일까지 남은 일수 (명세 CHL-01 {@code daysLeft}).
     *
     * <p>명세 예시가 정의다: {@code startDate} 11-04, {@code endDate} 11-10, 오늘 11-04 →
     * <b>6</b>. 즉 {@code endDate − 오늘} 이고 종료일 당일은 0 이다 — 기간의 길이(7일)와는
     * 다른 값이다.
     *
     * <p>"오늘"은 {@link KstTime#today()} 다. {@code LocalDate.now()} 를 쓰면 서버 JVM 의
     * 기본 시간대에 따라 하루가 밀려, 같은 순간에 조회한 챌린지의 남은 일수가 분석 화면의
     * 날짜 기준과 어긋난다.
     *
     * <p><b>음수는 0 으로 막는다.</b> 종료된 챌린지({@code SUCCESS}·{@code FAILED})는
     * {@code endDate} 가 과거라 그대로 두면 {@code -34} 같은 값이 나가고, 화면의 "D-N"
     * 표기가 깨진다. 명세는 이 경우를 정하지 않았다 — 0 이면 "남은 날 없음"으로 읽히고
     * 종료 여부는 {@code status} 가 이미 말해 준다.
     */
    private static long daysLeftOf(LocalDate endDate) {
        return Math.max(0, endDate.toEpochDay() - KstTime.today().toEpochDay());
    }
}
