package com.back.was.domain.nudge;

/**
 * ERD 6장 {@code NUDGE.STATUS} — 사용자가 카드에 어떻게 반응했는지.
 *
 * <p>순서가 아니라 집합이다. {@link #UNREAD} 만 홈에 노출되고(NDG-01), 나머지는 "이미
 * 반응한" 상태다. 판정이 다시 돌아 넛지가 갱신돼도 <b>상태는 유지</b>되고(ERD 6장 "닫은
 * 넛지는 닫힌 상태로 남음"), 조건 미달로 떨어질 때 {@code UNREAD} 만 삭제된다 — 그래서
 * "읽지 않음"과 "반응함"의 구분이 이 열거형의 핵심이다.
 */
public enum NudgeStatus {

    /** 미확인. NDG-01 이 홈에 노출하는 유일한 상태다. */
    UNREAD,

    /** 홈 카드 "닫기" (NDG-03). 같은 주에 갱신돼도 다시 노출되지 않는다. */
    DISMISSED,

    /** "소비 현황 확인" — 상세를 열어 본 상태. */
    VIEWED,

    /** AI 코칭 시작 (COACH-01 에 {@code nudgeId} 가 오면 서버가 자동으로 바꾼다). */
    COACHING_STARTED
}
