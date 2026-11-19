package com.back.was.analysis;

import com.back.was.category.CategoryRef;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** ANL-03 응답 data */
public record CategoryComparison(
        String period,
        String startDate,
        String endDate,
        boolean dataSufficient,
        long totalAmount,
        List<Item> items
) {
    public record Item(
            CategoryRef category,
            long amount,
            Long averageAmount,
            Double growthRate,
            @JsonProperty("isAnomaly") boolean isAnomaly   // Jackson 이 "anomaly" 로 바꾸지 않게 고정
    ) {}
}
