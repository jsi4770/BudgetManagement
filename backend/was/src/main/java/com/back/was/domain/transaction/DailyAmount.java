package com.back.was.domain.transaction;

import java.time.LocalDate;

/**
 * 하루치 금액 한 줄. 쿼리 전용 타입이다.
 *
 * <p>ANL-02 가 주별 막대를 만드는 재료다. 주 단위로 {@code group by} 하지 않는 이유는
 * 주 경계가 DB 함수(월요일 시작, KST)에 의존하게 되어 Postgres·H2 에서 같은 결과를
 * 보장하기 어렵기 때문이다 — 일별로 받아 {@link com.back.was.global.support.Weeks} 가
 * 정한 구간에 담으면 주 경계의 정의가 한 곳에만 남는다.
 *
 * <p>수입/지출 구분이 없는 건 쓰는 쪽이 지출만 집계하기 때문이다
 * ({@link DailyAmountSum} 은 TX-03 달력용으로 둘을 모두 갖는다).
 */
public record DailyAmount(LocalDate date, long amount) {
}
