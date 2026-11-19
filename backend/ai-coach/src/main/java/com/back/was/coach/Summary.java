package com.back.was.coach;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/** 월간 소비 요약. 모든 숫자는 코드가 계산하고, LLM 은 이 값만 인용한다. JSON 키는 snake_case (UI·프롬프트 호환) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Summary(
        String persona, String name, String month, List<String> monthsCovered,
        long income, long spending, long saving, long fixed, long variable,
        double fixedRatio, double savingsRate,
        SubscriptionInfo subscription,
        List<CategoryStat> categories,
        Map<String, Double> lateMonthShare,
        List<Merchant> topMerchants,
        List<Anomaly> anomalies,
        Map<String, Long> monthlyTotals) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CategoryStat(String category, long amount, double share, long prevAmount, Double momPct,
                               long baselineAvg, Double vsBaselinePct, long trendPerMonth, int count) {}

    public record Anomaly(String kind, String category, String detail, long value) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SubscriptionInfo(int count, int prevCount, long monthlyTotal, List<String> newServices) {}

    public record Merchant(String merchant, long amount, int count) {}
}
