package com.back.was.domain.admin.dto;

import com.back.was.domain.challenge.ChallengeOutcome;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ADMIN-01 응답 — 프로젝트 성공 지표 (명세 ADMIN-01 · ERD 13장).
 *
 * <p>별도 테이블이 없다. {@code CHALLENGE.STATUS} 를 집계해서 만든다(ERD 13장) — 그래서
 * 이 숫자는 챌린지가 종료될 때마다(스케줄러 또는 DEV-02) 저절로 움직인다.
 *
 * <p>{@code target} 과 {@code isAchieved} 를 서버가 담는다. 70% 라는 목표는
 * 프로젝트 KPI(ERD 13장)이고 화면에 박아 두면 둘이 어긋날 수 있다 — 발표 자료의 숫자와
 * API 의 숫자가 다르면 그 자리에서 설명이 안 된다.
 *
 * @param endedChallengeCount   종료된 챌린지 수 ({@code SUCCESS} + {@code FAILED})
 * @param successChallengeCount 그중 달성한 수
 * @param achievementRate       달성률(%). {@code 성공 ÷ 종료 × 100}, 소수점 2자리
 * @param target                KPI 목표 70.00
 * @param isAchieved            {@code achievementRate >= target}
 */
public record KpiResponse(
        long endedChallengeCount,
        long successChallengeCount,
        BigDecimal achievementRate,
        BigDecimal target,
        boolean isAchieved
) {
    /** ERD 13장: "프로젝트 KPI인 챌린지 달성률 70%". */
    private static final BigDecimal TARGET = new BigDecimal("70.00");

    /** CLAUDE.md: 비율은 {@code BigDecimal} scale 2. ERD 13장의 SQL 도 {@code ROUND(..., 2)} 다. */
    private static final int RATE_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * <p><b>종료된 챌린지가 없으면 달성률은 {@code 0.00} 이다.</b> 명세가 그 경우를 정하지
     * 않았는데, {@code null} 로 내리면 화면의 게이지가 NaN 이 되고 나누면 0 으로 나누기다.
     * {@code endedChallengeCount} 가 0 이라는 사실이 "아직 집계할 게 없다"를 이미 말해
     * 주므로 비율은 0 으로 둔다 — ANL-01 이 예산 0 에 {@code budgetUsageRate: 0.00} 을 주는
     * 것과 같은 판단이다.
     *
     * <p>그때 {@code isAchieved} 는 {@code false} 다. 0% 가 70% 를 넘지 못하는 건 맞고,
     * "데이터가 없으니 달성"으로 보이면 발표에서 오해를 부른다.
     */
    public static KpiResponse from(ChallengeOutcome outcome) {
        BigDecimal rate = outcome.endedCount() == 0
                ? BigDecimal.ZERO.setScale(RATE_SCALE)
                : BigDecimal.valueOf(outcome.successCount())
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(outcome.endedCount()), RATE_SCALE,
                                RoundingMode.HALF_UP);

        return new KpiResponse(
                outcome.endedCount(),
                outcome.successCount(),
                rate,
                TARGET,
                rate.compareTo(TARGET) >= 0);
    }
}
