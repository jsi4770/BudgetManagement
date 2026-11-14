package com.back.was.domain.coaching;

/**
 * 절약 목표의 제목 템플릿 — {@code docs/AI_대체_문구.md} <b>6장</b>.
 *
 * <p>AI 가 제목을 주지 못했을 때 쓰는 기본값이다. 쓰이는 곳이 셋이다: 규칙 기반 제안
 * (규칙 기반 제안기 — 아직 없음), AI 가 제목만 비워 보낸 경우(COACH-03), 그리고
 * 사용자가 제목을 손대지 않고 확정한 경우(COACH-04 "기본값은 AI 제안 제목, 없으면 템플릿").
 *
 * <p>{@code NudgeTitle} 과 같은 성격이다 — 템플릿은 <b>저장하지 않고</b> 필요할 때 조합한다.
 */
public final class GoalTitle {

    /** 6장 {@code AMOUNT} 템플릿. */
    private static final String AMOUNT_TEMPLATE = "%s %s원 이내";

    /** 6장 {@code COUNT} 템플릿. */
    private static final String COUNT_TEMPLATE = "%s %s회 이하";

    private GoalTitle() {
    }

    /**
     * @param goal 목표 금액 또는 횟수 — 어느 쪽인지는 {@code goalType} 이 정한다
     *             ({@code docs/AI_대체_문구.md} 변수 목록의 {@code {goal}})
     */
    public static String of(String categoryName, GoalType goalType, int goal) {
        // 금액은 천 단위 쉼표 (AI_대체_문구 머리말). 횟수는 쉼표가 붙을 만큼 커지지 않지만
        // 같은 서식을 써도 결과가 달라지지 않는다.
        String formatted = String.format("%,d", goal);

        return switch (goalType) {
            case AMOUNT -> AMOUNT_TEMPLATE.formatted(categoryName, formatted);
            case COUNT -> COUNT_TEMPLATE.formatted(categoryName, formatted);
        };
    }
}
