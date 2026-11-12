package com.back.was.domain.dev.dto;

import com.back.was.domain.dev.DemoScenario;

/**
 * DEV-01 응답.
 *
 * <p>심은 데이터를 되돌려주지 않는다 — 206건을 응답에 담을 이유가 없고, 화면은 바로 홈을
 * 다시 불러 확인한다. 알려 줄 것은 <b>무엇을 깔았고 몇 건인지</b>다.
 *
 * <p>명세 DEV-01 에는 <b>{@code nudgeCreated} 필드가 하나 더</b> 있다 — 시연자가
 * "배달비 증가 상황"을 눌렀을 때 넛지가 실제로 떴는지 홈에 들어가기 전에 알려 주는 값이다.
 * 넛지 도메인이 미구현이라 아직 넣지 않았다. 판정을 붙일 때 NDG-01 과 <b>같은 조건</b>으로
 * 되살릴 것(이번 주 월요일 · {@code UNREAD} 카드가 있는지).
 *
 * @param transactionCount 삽입한 거래 건수 (D = 2026-11-04 기준 206건)
 */
public record SeedResponse(
        DemoScenario scenario,
        int transactionCount
) {
}
