package com.back.was.coaching;

/**
 * AI 가 제안한 금액 목표 (챌린지는 금액 목표만 사용 - 팀 결정).
 * 프론트 확인 화면에서 사용자가 금액·기간을 수정할 수 있다 (SFR-025).
 */
public record GoalProposal(
        Long categoryId,
        String categoryName,
        long targetAmount,
        String startDate,
        String endDate,
        String reason
) {}
