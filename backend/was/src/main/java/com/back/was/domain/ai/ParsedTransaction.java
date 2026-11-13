package com.back.was.domain.ai;

import java.time.LocalDate;

/**
 * {@link TransactionTextParser} 출력 — 모델이 뽑아낸 값 그대로다.
 *
 * <p><b>뽑지 못한 필드는 {@code null} 이다.</b> 명세 TX-07 의 {@code missingFields} 는
 * 이 {@code null} 들로부터 서비스가 만든다. 모델이 "못 뽑았다"고 말한 목록을 그대로
 * 믿지 않는 이유는, 값과 목록이 어긋나는 응답(값은 줬는데 missing 이라고 하거나 그 반대)
 * 을 계약으로 흘려보낼 수 없기 때문이다. 어댑터는 모델이 무엇을 채웠든 "값이 없으면
 * null" 로만 정규화하면 된다.
 *
 * <p>미래 날짜도 그대로 담는다 — 명세 TX-07 은 사용자가 고칠 기회를 주고 저장
 * 시점(TX-01)에 거부하라고 한다.
 *
 * @param categoryId 모델이 고른 카테고리의 PK. 목록에 없는 값이면 서비스가 "카테고리
 *                   추출 실패"로 처리한다.
 */
public record ParsedTransaction(
        Integer amount,
        LocalDate transactionDate,
        Long categoryId,
        String memo
) {
}
