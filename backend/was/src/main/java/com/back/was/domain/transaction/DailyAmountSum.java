package com.back.was.domain.transaction;

import java.time.LocalDate;

/**
 * TX-03 집계 쿼리의 결과 한 줄. 응답 DTO 가 아니라 <b>쿼리 전용 타입</b>이다.
 *
 * <p>합계가 {@code long} 인 이유는 JPQL {@code sum()} 이 {@code Long} 을 돌려주기
 * 때문이다. 명세의 금액은 int(원)이라 응답으로 나갈 때 좁혀진다
 * ({@code TransactionService}).
 */
public record DailyAmountSum(
        LocalDate date,
        long income,
        long expense
) {
}
