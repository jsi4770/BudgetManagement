package com.back.was.domain.dev;

import com.back.was.domain.challenge.ChallengeStatus;

/**
 * DEV-02 가 강제로 만들어 낼 결과 (명세 DEV-02 표).
 *
 * <p>값이 {@link ChallengeStatus} 의 두 값과 겹쳐 보이지만 <b>다른 타입이어야 한다</b> —
 * 이쪽은 "어떤 시나리오를 연출할까"이고 저쪽은 "판정 결과가 무엇인가"다. 같은 enum 을
 * 받으면 클라이언트가 {@code IN_PROGRESS} 를 보낼 수 있게 되고, 그건 "강제 종료" API 에
 * 의미가 없는 요청이다. NDG-03 이 {@code NudgeReaction} 을 따로 둔 것과 같은 판단이다.
 *
 * <p><b>{@code null} 이 세 번째 선택지다</b> — 명세 DEV-02 표의 첫 줄("기간 종료 · 결과
 * 보기"): 거래를 추가하지 않고 현재 기록 그대로 판정한다. 그래서 enum 에
 * {@code AS_IS} 같은 값을 넣지 않았다. 명세의 요청 필드가 선택이라는 사실을 타입으로
 * 옮기면 {@code Optional}/{@code null} 이 그 자리다.
 */
public enum DevChallengeScenario {

    /** 달성 시나리오 — 오늘 날짜로 거래를 추가해 목표 이하로 맞춘다. */
    SUCCESS,

    /** 미달성 시나리오 — 오늘 날짜로 거래를 추가해 목표를 넘긴다. */
    FAILED
}
