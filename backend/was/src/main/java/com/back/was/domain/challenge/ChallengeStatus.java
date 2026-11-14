package com.back.was.domain.challenge;

/**
 * ERD 9장 {@code CHALLENGE.STATUS} — 달성 상태.
 *
 * <p>{@code IN_PROGRESS} 에서만 나가고 돌아오는 길은 없다. 전이는 <b>일일 스케줄러의 종료
 * 판정</b>이 한 번만 한다(ERD 9장: 매일 00:05 KST, {@code END_DATE < 오늘 AND
 * STATUS = 'IN_PROGRESS'}).
 *
 * <p>종료된 챌린지는 <b>얼어붙는다</b>: 거래 후처리 2번(실적 재계산)이 건드리지 않고
 * (서버_내부_처리 1장), 종료일 다음 날 00:05 이후에 기록한 거래는 결과를 바꾸지 않는다
 * (ERD 9장 "늦은 기록은 반영하지 않아요").
 *
 * <p>{@code IN_PROGRESS} 는 사용자당 하나다 — ERD 9장 "등록 규칙". 그 하나가 있는 동안
 * 코칭 시작과 목표 확정이 모두 막힌다({@link ChallengeRepository#existsInProgressByUserId}).
 */
public enum ChallengeStatus {

    /** 진행 중. 사용자당 최대 하나. */
    IN_PROGRESS,

    /** 달성 — 기간 내 실적이 목표 이하였다 (ERD 9장 판정). */
    SUCCESS,

    /** 미달성. */
    FAILED
}
