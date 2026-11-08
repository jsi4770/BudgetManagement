package com.back.was.domain.nudge.dto;

import com.back.was.domain.category.dto.CategoryRefResponse;
import com.back.was.domain.nudge.Nudge;
import com.back.was.global.support.KstTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * NDG-02 넛지 상세 응답 — 명세 NDG-02 "NDG-01 필드 + 아래 2개".
 *
 * <p>{@link NudgeResponse} 를 감싸지 않고 필드를 펼쳐 적은 이유: 명세의 JSON 은 평평한
 * 키 13개다. 중첩 레코드로 조합하면 {@code {"nudge": {...}, "aiAnalysis": ...}} 모양이 되고,
 * {@code @JsonUnwrapped} 로 억지로 펴면 응답 모양이 애노테이션 한 줄에 매달린다. 키 집합이
 * 계약이므로 각 응답을 그 모양 그대로 적는 쪽이 안전하다
 * ({@code TransactionListItem} vs {@code TransactionResponse} 와 같은 선택).
 *
 * <p>NDG-03 의 응답도 "NDG-02와 같은 형식"이라 이 타입을 함께 쓴다.
 *
 * <p>{@code zScore} 는 여기에도 없다 — 명세가 더하는 건 AI 문구 두 개뿐이다.
 *
 * @param aiAnalysis       넛지 저장 후 <b>비동기</b> 생성. 생성 전에는 {@code null} 이고,
 *                         프론트는 그 상태를 "AI가 분석 중이에요"로 표시한 뒤 재조회한다
 * @param aiRecommendation 같은 규칙. 문장으로 된 조언이고, 수치 목표 제안은 COACHING 쪽이다
 *                         (ERD 6장)
 */
public record NudgeDetailResponse(
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
        OffsetDateTime updatedAt,
        String aiAnalysis,
        String aiRecommendation
) {
    public static NudgeDetailResponse from(Nudge nudge) {
        return new NudgeDetailResponse(
                nudge.getId(),
                CategoryRefResponse.from(nudge.getCategory()),
                NudgeTitle.of(nudge.getCategory()),
                nudge.getAnalysisStartDate(),
                nudge.getAnalysisEndDate(),
                nudge.getSpendingAmount(),
                nudge.getAvgAmount(),
                nudge.getGrowthRate(),
                nudge.getStatus().name(),
                KstTime.toOffset(nudge.getCreatedAt()),
                KstTime.toOffset(nudge.getUpdatedAt()),
                nudge.getAiAnalysis(),
                nudge.getAiRecommendation());
    }
}
