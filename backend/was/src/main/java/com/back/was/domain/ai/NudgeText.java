package com.back.was.domain.ai;

/**
 * 넛지 상세(NDG-02)의 두 문구. {@code NUDGE.AI_ANALYSIS} · {@code AI_RECOMMENDATION} 에
 * 그대로 저장된다.
 *
 * @param analysis       무슨 일이 있었는지 설명하는 문장 (AI_대체_문구 1장)
 * @param recommendation 문장으로 된 조언 (2장). 수치 목표 제안은 COACHING 쪽이다 (ERD 6장)
 */
public record NudgeText(String analysis, String recommendation) {
}
