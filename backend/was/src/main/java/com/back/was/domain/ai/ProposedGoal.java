package com.back.was.domain.ai;

import com.back.was.domain.coaching.GoalType;

/**
 * 제안된 절약 목표 하나 — 명세 COACH-03 {@code proposal} 의 원재료이고 ERD 7장
 * {@code PROPOSAL_} 컬럼 6개와 1:1 이다.
 *
 * <p>두 곳에서 만들어진다. <b>같은 타입인 것이 핵심</b>이다 — 저장·검증·응답 변환 코드가
 * 한 벌이면 두 경로가 어긋날 수 없다.
 *
 * <ul>
 *   <li>Gemini 구조화 출력 (명세 COACH-03 "{@code proposal} 은 Gemini 구조화 출력으로 수신")</li>
 *   <li>서버의 규칙 기반 제안 ({@code docs/AI_대체_문구.md} 5장 — Gemini 가 죽어도
 *       "넛지 → 코칭 → 챌린지 확정" 흐름이 끊기지 않게)</li>
 * </ul>
 *
 * <p><b>필드는 전부 신뢰하지 않는다.</b> 모델이 채운 값이 올 수 있으므로 저장하는 쪽
 * ({@code CoachingService}) 이 카테고리 존재와 ERD 7장 체크 조건을 확인하고, 통과하지
 * 못하면 제안 없이({@code proposal: null}) 응답한다 — 틀린 제안을 저장하면 그대로
 * 챌린지로 확정될 수 있다.
 *
 * <p>{@code title} 이 비어 있으면 {@code GoalTitle}(AI_대체_문구 6장)이 채운다 — 명세
 * COACH-04 의 "기본값은 AI 제안 제목, 없으면 템플릿"이 그 규칙이다.
 *
 * @param categoryId  대상 카테고리. DB 에 없는 ID 면 제안이 버려진다
 * @param title       제안 제목. {@code null}·공백이면 템플릿으로 채운다
 * @param goalType    {@code AMOUNT} / {@code COUNT}
 * @param goalAmount  {@code goalType == AMOUNT} 일 때 필수 (ERD 7장 체크 조건)
 * @param goalCount   {@code goalType == COUNT} 일 때 필수
 * @param periodDays  기간(일). 명세 COACH-04 의 선택지는 7·14·30 이다
 */
public record ProposedGoal(
        Long categoryId,
        String title,
        GoalType goalType,
        Integer goalAmount,
        Integer goalCount,
        Integer periodDays
) {
}
