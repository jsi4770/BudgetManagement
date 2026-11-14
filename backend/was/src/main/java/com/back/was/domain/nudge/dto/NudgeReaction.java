package com.back.was.domain.nudge.dto;

import com.back.was.domain.nudge.NudgeStatus;

/**
 * NDG-03 으로 <b>클라이언트가 보낼 수 있는</b> 상태. 명세 NDG-03 의 표 그대로 세 개다.
 *
 * <p>{@link NudgeStatus} 를 요청 타입으로 바로 쓰지 않는 이유: 그러면 {@code UNREAD} 도
 * 받아들여져 <b>닫은 카드를 다시 미확인으로 되돌릴 수</b> 있다. 명세 NDG-03 은 "닫은 넛지는
 * 재노출되지 않음"이고 화면에도 그런 동작이 없다. 열거형에 없는 값은 Jackson 역직렬화에서
 * 걸러져 400 {@code VALIDATION_ERROR} 가 되므로, {@code "UNREAD"} 든 {@code "FOO"} 든
 * <b>같은 방식으로</b> 거부된다 — 검증을 따로 붙이면 둘의 에러 모양이 달라진다.
 *
 * <p>{@code UNREAD} 로 되돌리는 건 판정(서버_내부_처리 3장)도 하지 않는다. 조건이 꺾이면
 * {@code UNREAD} 카드를 <b>삭제</b>하고, 이미 반응한 카드는 그대로 둔다.
 */
public enum NudgeReaction {

    /** 홈 카드 "닫기". */
    DISMISSED(NudgeStatus.DISMISSED),

    /** "소비 현황 확인". */
    VIEWED(NudgeStatus.VIEWED),

    /**
     * "AI 코칭 시작". COACH-01 에 {@code nudgeId} 를 주면 서버가 알아서 바꾸므로
     * (서버_내부_처리 6장) 프론트가 이 값을 직접 보내는 일은 보통 없다.
     */
    COACHING_STARTED(NudgeStatus.COACHING_STARTED);

    private final NudgeStatus status;

    NudgeReaction(NudgeStatus status) {
        this.status = status;
    }

    /** 이름이 아니라 명시적인 짝으로 변환한다 — 한쪽 이름이 바뀌어도 조용히 깨지지 않는다. */
    public NudgeStatus toStatus() {
        return status;
    }
}
