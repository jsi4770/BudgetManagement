package com.back.was.domain.ai;

import java.math.BigDecimal;

/**
 * 넛지 문구를 만드는 데 필요한 값 — {@code docs/AI_대체_문구.md} "변수 목록" 중 넛지
 * 템플릿(1·2장)이 쓰는 것들이다.
 *
 * <p>엔티티를 넘기지 않는 이유는 {@code TextParseCommand} 와 같다: AI 어댑터는 DB·JPA 를
 * 몰라야 하고, 비동기 스레드에서 영속성 컨텍스트 밖의 엔티티를 만지면 지연 로딩이 터진다.
 *
 * @param categoryName     {@code {category}}
 * @param spendingAmount   {@code {amount}} — 이번 기간 금액
 * @param averageAmount    {@code {avg}} — 평소 이맘때 평균
 * @param growthRate       {@code {rate}} 의 원본. 템플릿에 넣을 때 소수점을 버린다
 * @param elapsedDays      {@code {days}} — 분석 기간의 일수 (월요일부터 센 k)
 * @param transactionCount {@code {count}} — 분석 기간의 거래 횟수
 */
public record NudgeTextCommand(
        String categoryName,
        int spendingAmount,
        int averageAmount,
        BigDecimal growthRate,
        int elapsedDays,
        int transactionCount
) {
    /** {@code {diff}} — 금액 − 평균. 넛지가 생겼다는 건 이 값이 30,000원 이상이라는 뜻이다. */
    public int difference() {
        return spendingAmount - averageAmount;
    }
}
