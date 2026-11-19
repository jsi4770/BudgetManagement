package com.back.was.domain.report.dto;

import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.report.GrowthReport;
import com.back.was.global.support.KstTime;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * CHL-03 성장 리포트 응답 (명세 CHL-03).
 *
 * <p>리포트 테이블과 챌린지를 합친 모양이다 — 명세의 출처 표가 그렇게 적어 두었다:
 * {@code before} 는 {@code GROWTH_REPORT}, {@code during} 은
 * {@code CHALLENGE.SPENDING_*}, {@code feedback} 은 {@code GROWTH_REPORT.FEEDBACK}.
 *
 * <p>{@code difference} 는 <b>계산값이고 저장하지 않는다</b>(명세 CHL-03 출처 표):
 * {@code 목표 − 실적} 이라 <b>양수면 목표보다 덜 썼다</b>는 뜻이다. 부호를 유지하는 것이
 * 중요하다 — 7장 피드백 문구의 {@code {gap}} 은 절댓값이지만, 화면은 넘겼는지 남겼는지를
 * 이 부호로 판단한다.
 *
 * <p><b>{@code goalCount} 는 명세 예시에 없지만 넣었다</b> (2026-11-10 결정). 예시가
 * {@code AMOUNT} 챌린지라 {@code goalAmount} 만 보이는데, {@code COUNT} 챌린지의 리포트는
 * 목표를 보여 줄 키가 없어진다 — {@code difference} 가 "목표 − 실적"이라 어느 목표인지
 * 알아야 읽힌다. CHL-01·02 도 두 키를 모두 내보내므로 모양이 일관된다. 프론트가 무시할
 * 키는 해롭지 않지만 필요한 키가 없는 건 버그다.
 *
 * <p>{@code spendingCount} 는 따로 두지 않았다 — {@code during.count} 가 같은 값이고,
 * 명세의 키 구성에도 없다.
 */
public record GrowthReportResponse(
        Long challengeId,
        String title,
        String status,
        String goalType,
        Integer goalAmount,
        Integer goalCount,
        int spendingAmount,
        int difference,
        BigDecimal achievementRate,
        Spending before,
        Spending during,
        String feedback,
        OffsetDateTime createdAt
) {
    /**
     * 한 기간의 금액·횟수 묶음 — 명세 CHL-03 의 {@code before}·{@code during} 이 같은 두
     * 키라 한 타입으로 쓴다.
     *
     * @param amount {@code before} 는 챌린지 직전 같은 길이 기간, {@code during} 은 챌린지 기간
     */
    public record Spending(
            int amount,
            int count
    ) {
    }

    public static GrowthReportResponse from(GrowthReport report) {
        Challenge challenge = report.getChallenge();
        int goal = challenge.getGoalType()
                .requiredGoal(challenge.getGoalAmount(), challenge.getGoalCount());
        int spending = challenge.getGoalType() == GoalType.AMOUNT
                ? challenge.getSpendingAmount()
                : challenge.getSpendingCount();

        return new GrowthReportResponse(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getStatus().name(),
                challenge.getGoalType().name(),
                challenge.getGoalAmount(),
                challenge.getGoalCount(),
                challenge.getSpendingAmount(),
                // 목표 − 실적. 유형이 고르는 짝으로 계산해야 COUNT 챌린지에서 금액과
                // 횟수를 섞지 않는다 (Challenge.finish() 의 판정과 같은 짝).
                goal - spending,
                challenge.getAchievementRate(),
                new Spending(report.getBeforeAmount(), report.getBeforeCount()),
                new Spending(challenge.getSpendingAmount(), challenge.getSpendingCount()),
                report.getFeedback(),
                // ERD 는 _AT 를 시간대 없는 DATETIME 으로 두지만 명세 0장 2)는 오프셋을 요구한다
                KstTime.toOffset(report.getCreatedAt()));
    }
}
