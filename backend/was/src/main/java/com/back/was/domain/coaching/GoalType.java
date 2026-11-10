package com.back.was.domain.coaching;

/**
 * 절약 목표를 무엇으로 세는지 — ERD 7장 {@code COACHING.PROPOSAL_GOAL_TYPE} 과 9장
 * {@code CHALLENGE.GOAL_TYPE} 이 <b>같은 두 값</b>이다.
 *
 * <p>두 테이블에 같은 enum 이 들어가는 건 중복이 아니라 <b>스냅샷</b>이다(ERD 9장):
 * 제안은 AI 가 말한 값이고 챌린지는 사용자가 확정한 값이라, 같은 의미의 컬럼이 양쪽에
 * 따로 남는다. 그래서 타입은 하나만 두고 {@code domain.challenge} 가 이것을 가져다 쓴다 —
 * 두 벌을 만들면 {@code valueOf} 로 옮기는 코드가 생기고, 한쪽에 값을 추가했을 때 다른
 * 쪽이 조용히 터진다.
 *
 * <p>어느 금액 컬럼이 채워져야 하는지는 이 값이 정한다 (ERD 7장 체크 조건) —
 * {@link #requiredGoal(Integer, Integer)} 가 그 규칙이다.
 */
public enum GoalType {

    /** 금액 목표 — "{카테고리} {금액}원 이내". {@code GOAL_AMOUNT} 가 필수다. */
    AMOUNT,

    /** 횟수 목표 — "{카테고리} {횟수}회 이하". {@code GOAL_COUNT} 가 필수다. */
    COUNT;

    /**
     * 이 유형이 쓰는 목표 값을 골라 돌려준다 — ERD 7장의 체크 조건
     * ({@code AMOUNT} 면 금액, {@code COUNT} 면 횟수가 {@code NOT NULL})을 한 곳에 모은
     * 것이다. 저장할 때마다 {@code if (goalType == AMOUNT)} 를 다시 쓰면 한 군데에서
     * 빠뜨린다.
     *
     * @throws IllegalArgumentException 이 유형이 쓰는 값이 비었거나 1 미만일 때
     */
    public int requiredGoal(Integer goalAmount, Integer goalCount) {
        Integer goal = this == AMOUNT ? goalAmount : goalCount;
        if (goal == null || goal < 1) {
            throw new IllegalArgumentException(
                    "목표 유형이 %s 면 %s 가 1 이상이어야 해요 (ERD 7장 체크 조건)."
                            .formatted(this, this == AMOUNT ? "goalAmount" : "goalCount"));
        }
        return goal;
    }
}
