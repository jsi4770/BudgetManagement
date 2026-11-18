package com.back.was.domain.ai;

/**
 * 코드가 계산한 최근 지출 사실 한 줄. 모델은 이 숫자를 <b>그대로 인용만</b> 한다(숫자는 코드, 해석은 LLM).
 *
 * @param categoryName 지출 카테고리 이름
 * @param amount       기간 합계(원)
 * @param count        기간 결제 횟수
 */
public record SpendingFact(String categoryName, long amount, long count) {
}
