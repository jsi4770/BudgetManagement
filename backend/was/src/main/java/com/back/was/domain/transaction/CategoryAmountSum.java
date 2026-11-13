package com.back.was.domain.transaction;

/**
 * 카테고리별 지출 합계 한 줄. 쿼리 전용 타입이다.
 *
 * <p>ANL-03 의 항목과 넛지 판정(서버_내부_처리 3장)이 같은 쿼리를 쓴다 — 명세가 둘에
 * "같은 계산 함수"를 요구하기 때문이다. 분석 기간과 표본 구간 모두 이 타입으로 읽어
 * 비교한다.
 *
 * <p>{@code name} 을 함께 들고 오는 이유: 응답({@code category: {id, name}})과 넛지 카드
 * 제목 템플릿이 이름을 쓰는데, ID 만 받아 오면 카테고리를 다시 조회해야 한다.
 */
public record CategoryAmountSum(Long categoryId, String categoryName, long amount) {
}
