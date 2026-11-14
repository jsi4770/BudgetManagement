package com.back.was.domain.coaching;

/**
 * ERD 7장 {@code COACHING.STATUS} — 코칭 세션의 상태 세 개.
 *
 * <p>전이는 한 방향뿐이고 {@code IN_PROGRESS} 에서만 나간다:
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} → {@code COMPLETED} — COACH-04 로 챌린지를 확정했다.
 *       종료 시각은 따로 저장하지 않고 {@code CHALLENGE.CREATED_AT} 이 대신한다.</li>
 *   <li>{@code IN_PROGRESS} → {@code CANCELED} — 확정하지 않은 채 <b>새 코칭을
 *       시작</b>해서 밀려났다(COACH-01). 취소 시각은 쓰는 곳이 없어 저장하지 않는다
 *       (ERD 7장 "시작·종료 시각" 표).</li>
 * </ul>
 *
 * <p>{@code COMPLETED} 와 {@code CANCELED} 는 둘 다 <b>종료</b>다 — 그 코칭에 들어오는
 * COACH-03·04 요청은 409 {@code COACHING_ALREADY_ENDED} 다(서버_내부_처리 6장). 그래서
 * 두 값을 구분해서 묻는 코드보다 {@link Coaching#isEnded()} 를 쓰는 쪽이 맞다.
 */
public enum CoachingStatus {

    /** 대화 중. 사용자당 최대 하나다 — COACH-01 이 시작할 때 이전 것을 취소하기 때문이다. */
    IN_PROGRESS,

    /** 챌린지 확정으로 종료 (COACH-04). */
    COMPLETED,

    /** 확정 없이 새 코칭이 시작돼 취소됨 (COACH-01). */
    CANCELED
}
