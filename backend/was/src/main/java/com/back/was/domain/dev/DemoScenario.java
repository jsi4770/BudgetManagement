package com.back.was.domain.dev;

/**
 * DEV-01 이 깔 시연 시나리오 (명세 DEV-01 표 · 시연_데이터 3-4).
 *
 * <p>둘의 차이는 <b>이번 주 배달 거래</b>뿐이다. 과거 3개월은 완전히 같고, 그래서 같은
 * 화면에서 "넛지가 뜨는 상태"와 "뜨지 않는 상태"를 번갈아 보여 줄 수 있다.
 */
public enum DemoScenario {

    /** 직전 3개월 + 이번 달 평소 수준 → 넛지 없음. 이번 주 배달 58,000원 2회. */
    NORMAL,

    /** {@code NORMAL} + 이번 주 배달 급증 → 넛지 생성. 이번 주 배달 101,000원 4회. */
    DELIVERY_SPIKE
}
