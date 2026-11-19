package com.back.was.nudge;

import java.time.LocalDate;

/**
 * 넛지 저장값 (NDG-01 필드 기준). title 은 저장하지 않고 NudgeTitle 로 조합 (명세).
 * status: UNREAD | VIEWED | DISMISSED | COACHING_STARTED
 */
public record NudgeRecord(
        Long id,
        Long userId,
        Long categoryId,
        LocalDate analysisStartDate,   // 이번 주 월요일
        LocalDate analysisEndDate,     // 판정한 날 (오늘)
        long spendingAmount,
        long avgAmount,
        Double growthRate,
        String status
) {
    public static final String UNREAD = "UNREAD";
    public static final String DISMISSED = "DISMISSED";

    /** 같은 주 재판정: 값만 갱신하고 상태는 유지 (계획표 NDG 규칙) */
    public NudgeRecord withValues(LocalDate endDate, long spending, long avg, Double growth) {
        return new NudgeRecord(id, userId, categoryId, analysisStartDate, endDate, spending, avg, growth, status);
    }
}
