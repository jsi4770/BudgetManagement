package com.back.was.domain.nudge.dto;

import com.back.was.domain.category.dto.CategoryRefResponse;
import com.back.was.domain.nudge.Nudge;
import com.back.was.global.support.KstTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * NDG-01 홈 노출 넛지 응답.
 *
 * <p><b>{@code zScore} 는 없다</b> — 명세 NDG-01 이 "Z-score는 내부 판정용으로 미포함"으로
 * 못박았다. 화면은 금액과 증가율만 쓴다.
 *
 * <p>NDG-02 상세는 여기에 {@code aiAnalysis}·{@code aiRecommendation} 두 개가 더 붙는
 * 별도 모양이므로(명세 NDG-02 "NDG-01 필드 + 아래 2개") 그때 타입을 따로 만든다 —
 * {@code TransactionListItem} 과 {@code TransactionResponse} 를 나눈 것과 같은 이유다.
 */
public record NudgeResponse(
        Long id,
        CategoryRefResponse category,
        String title,
        LocalDate analysisStartDate,
        LocalDate analysisEndDate,
        int spendingAmount,
        int avgAmount,
        BigDecimal growthRate,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * 명세 NDG-01 · ERD 6장: 제목은 DB 에 저장하지 않고 카테고리명으로 조합한다. 저장해
     * 두면 카테고리명이 바뀌었을 때 과거 카드만 옛 이름으로 남는다.
     */
    private static final String TITLE_TEMPLATE = "%s 지출이 평소보다 늘었어요";

    public static NudgeResponse from(Nudge nudge) {
        return new NudgeResponse(
                nudge.getId(),
                CategoryRefResponse.from(nudge.getCategory()),
                TITLE_TEMPLATE.formatted(nudge.getCategory().getName()),
                nudge.getAnalysisStartDate(),
                nudge.getAnalysisEndDate(),
                nudge.getSpendingAmount(),
                nudge.getAvgAmount(),
                nudge.getGrowthRate(),
                nudge.getStatus().name(),
                // ERD 는 _AT 를 시간대 없는 DATETIME 으로 두지만 명세 0장 2)는 오프셋을
                // 요구한다 — 직렬화 직전에 +09:00 을 붙인다.
                KstTime.toOffset(nudge.getCreatedAt()),
                KstTime.toOffset(nudge.getUpdatedAt()));
    }
}
