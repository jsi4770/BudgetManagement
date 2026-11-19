package com.back.was.nudge;

import java.util.List;

/**
 * AI 문구에 넣을 사실값. 전부 서버가 계산한다 (모델이 직접 더하거나 나누면 틀리기 때문).
 */
public record NudgeFacts(
        String categoryName,
        int days,             // 이번 주 경과 일수 (월요일~오늘)
        int count,            // 이번 주 해당 카테고리 거래 횟수
        long spendingAmount,
        long avgAmount,
        long diffAmount,
        Double growthRate,
        long perTxAvg,        // 1회 평균
        int weekendCount,
        List<String> memos    // 가게명 단서 (최대 6개)
) {}
