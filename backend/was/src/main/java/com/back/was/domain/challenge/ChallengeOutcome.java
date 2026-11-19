package com.back.was.domain.challenge;

/**
 * 종료된 챌린지의 집계 — ADMIN-01 의 KPI 재료 (ERD 13장).
 *
 * <p>두 숫자를 <b>한 쿼리로</b> 받는다. 달성률이 {@code 성공 ÷ 종료 × 100} 이라 분모와
 * 분자가 다른 시점의 집계면 100% 를 넘는 비율 같은 설명 불가능한 값이 나올 수 있다 —
 * {@code SpendingTotal} 과 같은 이유다.
 *
 * <p>{@code long} 인 이유는 JPQL {@code count()}·{@code sum()} 의 타입이 그래서다.
 *
 * @param endedCount   {@code SUCCESS} + {@code FAILED} (진행 중은 제외 — ERD 13장의 WHERE)
 * @param successCount 그중 {@code SUCCESS}
 */
public record ChallengeOutcome(
        long endedCount,
        long successCount
) {
}
