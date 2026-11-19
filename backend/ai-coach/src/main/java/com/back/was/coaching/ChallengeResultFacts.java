package com.back.was.coaching;

/**
 * 성장 리포트 AI 피드백(SFR-035)에 넣을 사실값. 전부 서버가 계산.
 *
 * @param beforeWeeklyAvg 챌린지 이전 4주 주간 평균 지출 (SFR-033 소비 변화 비교)
 * @param duringWeeklyAvg 챌린지 기간 주간 평균 지출
 */
public record ChallengeResultFacts(
        String categoryName,
        long targetAmount,
        long actualAmount,
        boolean achieved,
        String startDate,
        String endDate,
        Long beforeWeeklyAvg,
        Long duringWeeklyAvg
) {}
