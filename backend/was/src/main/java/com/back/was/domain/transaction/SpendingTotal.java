package com.back.was.domain.transaction;

/**
 * 한 카테고리·한 기간의 지출 합계와 건수 — 챌린지 실적 재계산(서버_내부_처리 2장)이 쓰는
 * 두 숫자다.
 *
 * <p>합계와 건수를 <b>한 쿼리로</b> 받는다. {@code CHALLENGE} 는 둘을 모두 저장하고
 * ({@code SPENDING_AMOUNT} · {@code SPENDING_COUNT}) 목표 유형에 따라 둘 중 하나로
 * 판정하므로, 따로 조회하면 거래 한 건을 바꿀 때마다 쿼리가 두 배가 된다. 더 중요한 건
 * <b>두 값이 같은 스냅샷에서 나와야</b> 한다는 점이다 — 금액과 건수가 서로 다른 시점의
 * 집계면 {@code ACHIEVEMENT_RATE} 가 설명할 수 없는 값이 된다.
 *
 * <p>{@code long} 인 이유는 JPQL {@code sum()}·{@code count()} 의 타입이 그래서다. 원 단위
 * {@code int} 로 좁히는 일은 저장하는 쪽({@code Challenge})이 한다.
 *
 * @param amount 기간 안의 금액 합계. 거래가 없으면 0 ({@code coalesce})
 * @param count  기간 안의 거래 건수
 */
public record SpendingTotal(
        long amount,
        long count
) {
}
