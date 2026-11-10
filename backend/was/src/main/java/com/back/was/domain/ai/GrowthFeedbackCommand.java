package com.back.was.domain.ai;

import com.back.was.domain.coaching.GoalType;

/**
 * 성장 리포트 피드백을 만드는 데 필요한 값 — {@code docs/AI_대체_문구.md} 7장 템플릿이
 * 쓰는 변수들이다.
 *
 * <p>엔티티({@code Challenge}·{@code GrowthReport})를 넘기지 않는 이유는 다른 커맨드들과
 * 같다: AI 어댑터는 DB·JPA 를 몰라야 한다.
 *
 * @param success    달성했는지. 7장이 문구를 성공/실패 두 묶음으로 나눈 기준이다
 * @param goalType   {@code {gap}} 의 단위를 정한다 — 7장 각주: "횟수 목표일 때는
 *                   {@code {gap}원} 을 {@code {gap}회} 로 바꿔서 써요"
 * @param goal       목표 금액 또는 횟수
 * @param spending   챌린지 기간의 실제 금액 또는 횟수 (같은 단위)
 * @param beforeAmount {@code {before}} — 챌린지 직전 같은 길이 기간의 <b>금액</b>.
 *                     횟수 목표라도 금액이다 (7장 각주는 {@code {gap}} 만 바꾼다)
 * @param periodDays {@code {period}} — 챌린지 기간(일)
 */
public record GrowthFeedbackCommand(
        boolean success,
        GoalType goalType,
        int goal,
        int spending,
        int beforeAmount,
        int periodDays
) {
    /**
     * {@code {gap}} — 목표와 실제의 차이, <b>절댓값</b>이다
     * ({@code docs/AI_대체_문구.md} 변수 목록).
     *
     * <p>부호가 없어도 되는 건 문구가 방향을 이미 말하기 때문이다: 성공 문구는 "덜 썼어요",
     * 실패 문구는 "더 썼어요".
     */
    public int gap() {
        return Math.abs(goal - spending);
    }
}
