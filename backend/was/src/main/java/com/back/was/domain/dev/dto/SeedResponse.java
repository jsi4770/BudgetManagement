package com.back.was.domain.dev.dto;

import com.back.was.domain.dev.DemoScenario;

/**
 * DEV-01 응답 (명세 DEV-01).
 *
 * <p>심은 데이터를 되돌려주지 않는다 — 206건을 응답에 담을 이유가 없고, 화면은 바로 홈을
 * 다시 불러 확인한다. 알려 줄 것은 <b>무엇을 깔았고 몇 건인지</b>, 그리고 <b>넛지가
 * 생겼는지</b>다. 마지막 값이 중요한 이유: 시연자가 "배달비 증가 상황"을 눌렀을 때 넛지가
 * 실제로 떴는지 홈에 들어가기 전에 알 수 있다.
 *
 * @param transactionCount 삽입한 거래 건수 (D = 2026-11-04 기준 206건)
 * @param nudgeCreated     판정 결과 이번 주 넛지가 있는지. {@code NORMAL} 이면 {@code false}
 */
public record SeedResponse(
        DemoScenario scenario,
        int transactionCount,
        boolean nudgeCreated
) {
}
