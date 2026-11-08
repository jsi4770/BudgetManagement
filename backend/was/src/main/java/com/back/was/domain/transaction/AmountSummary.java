package com.back.was.domain.transaction;

/**
 * 기간 하나의 수입·지출 합계. 응답 DTO 가 아니라 <b>쿼리 전용 타입</b>이다
 * ({@link DailyAmountSum} 과 같은 성격).
 *
 * <p>ANL-01 의 {@code totalIncome}·{@code totalExpense} 원본이다. 거래에는 수입/지출
 * 컬럼이 없어서(ERD 5장) 두 값은 카테고리 TYPE 으로 갈라 집계된다.
 *
 * <p>{@code long} 인 이유는 JPQL {@code sum()} 이 {@code Long} 이기 때문이다. 명세의
 * 금액은 int(원)이라 응답으로 나갈 때 좁혀진다.
 */
public record AmountSummary(long income, long expense) {

    /** {@code totalIncome − totalExpense} (명세 ANL-01 {@code balance}). */
    public long balance() {
        return income - expense;
    }
}
