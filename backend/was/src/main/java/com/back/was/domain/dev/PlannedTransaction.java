package com.back.was.domain.dev;

import java.time.LocalDate;

/**
 * DEV-01 이 넣을 거래 한 건의 계획 — 아직 엔티티가 아니다.
 *
 * <p>{@link DemoTransactionPlanner} 를 DB 없이 테스트할 수 있게 하는 타입이다. 덕분에
 * 생성 규칙(시연_데이터 3장)을 {@code docs/시연_거래내역.csv} 와 직접 비교할 수 있다 —
 * 그 CSV 가 D = 2026-11-04 일 때의 정답지다.
 *
 * @param date       거래 날짜. 요일 보정(3-5)이 이미 적용된 값이다
 * @param categoryId ERD 4장 시드의 ID (1 식비 · 2 배달 · 3 카페 · 4 교통 · 5 월세 ·
 *                   6 기타 · 7 급여)
 */
public record PlannedTransaction(
        LocalDate date,
        long categoryId,
        int amount,
        String memo
) {
}
