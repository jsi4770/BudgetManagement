package com.back.was.analysis;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 이상치 판정 (SFR-017, SRS 7.2, ANL-03, NDG-01 공용 순수 함수).
 * 규칙: 가입 4주 이상 + Z ≥ 2 AND (이번 주 − 평균) ≥ 30,000원
 * ANL-03 WEEK 와 TX-01/05/06 후처리의 넛지 판정이 둘 다 이 함수를 쓴다.
 */
public final class AnomalyJudge {

    public static final double Z_THRESHOLD = 2.0;
    public static final long MIN_DIFF = 30_000L;
    public static final int SUFFICIENT_DAYS = 28;   // 가입 4주

    private AnomalyJudge() {}

    /**
     * @param averageAmount 과거 평균 (반올림)
     * @param growthRate    평균 대비 증가율 % (소수 둘째 자리). 평균 0원이면 null
     * @param zScore        내부 판정용 (응답에 안 내보냄)
     */
    public record Result(long averageAmount, Double growthRate, double zScore, boolean anomaly) {}

    /**
     * @param current 이번 기간 금액
     * @param past    비교 기간별 금액 (WEEK: 직전 4주, 빈 주는 0)
     */
    public static Result judge(long current, List<Long> past) {
        if (past == null || past.isEmpty()) {
            return new Result(0, null, 0, false);
        }
        double avg = past.stream().mapToLong(Long::longValue).average().orElse(0);
        // 모표준편차 (표본 4개, ddof=0). 팀 기준이 표본표준편차면 past.size()-1 로 나누기
        double variance = past.stream().mapToDouble(v -> (v - avg) * (v - avg)).sum() / past.size();
        double std = Math.sqrt(variance);
        double diff = current - avg;

        // 4주 내내 같은 금액이면 std = 0 → 늘었으면 무한대로 취급 (금액 조건이 함께 걸러 줌)
        double z = std > 0 ? diff / std : (diff > 0 ? Double.POSITIVE_INFINITY : 0);
        Double growth = avg > 0 ? round2(diff / avg * 100) : null;
        boolean anomaly = z >= Z_THRESHOLD && diff >= MIN_DIFF;

        return new Result(Math.round(avg), growth, z, anomaly);
    }

    /** 가입 4주 미만이면 false → 판단하지 않고 dataSufficient: false (SRS 7.2-2, 7.2-4) */
    public static boolean isDataSufficient(LocalDate signupDate, LocalDate baseDate) {
        return ChronoUnit.DAYS.between(signupDate, baseDate) >= SUFFICIENT_DAYS;
    }

    public static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
