package com.back.was.domain.coaching.dto;

import com.back.was.domain.category.dto.CategoryRefResponse;
import com.back.was.domain.coaching.Coaching;

/**
 * AI 가 제안한 절약 목표 — "AI 절약 목표 제안" 카드 (명세 COACH-02·03 {@code proposal}).
 *
 * <p>저장된 최신 제안 하나를 그대로 옮긴 모양이다(ERD 7장 {@code PROPOSAL_} 컬럼 6개).
 * 제안이 아직 없으면 이 객체가 아니라 <b>{@code proposal: null}</b> 이다 — COACH-01 응답이
 * 항상 그렇다.
 *
 * <p>{@code goalAmount} 와 {@code goalCount} 는 {@code goalType} 에 따라 한쪽만 값이
 * 있다(명세 예시의 {@code "goalCount": null}). {@code ApiResponse} 와 달리
 * {@code @JsonInclude} 를 걸지 않으므로 <b>두 키 모두 응답에 남는다</b> — 프론트의 ± 버튼이
 * 어느 쪽을 조정할지 키의 존재로 판단하지 않고 {@code goalType} 으로 판단하면 되고, 키가
 * 사라지면 명세의 키 구성과 어긋난다.
 *
 * <p>{@code category} 는 {@code {id, name}} 이다(명세 COACH-02·03 예시) — ANL-03 항목과
 * 넛지가 쓰는 {@link CategoryRefResponse} 와 같은 키 구성이라 그 타입을 재사용한다.
 *
 * <p>확정(COACH-04)할 때 프론트는 이 값을 기본값으로 쓰되 사용자가 ± 로 바꾼 최종 값을
 * 보낸다. 그래서 이 응답은 <b>기본값</b>이고 계약이 아니다(ERD 7장 스냅샷 규칙).
 */
public record CoachingProposalResponse(
        CategoryRefResponse category,
        String goalType,
        Integer goalAmount,
        Integer goalCount,
        Integer periodDays,
        String title
) {
    /** @return 제안이 아직 없으면 {@code null} — 호출자가 그대로 {@code proposal} 에 넣는다 */
    public static CoachingProposalResponse from(Coaching coaching) {
        if (!coaching.hasProposal()) {
            return null;
        }
        return new CoachingProposalResponse(
                // 제안 카테고리는 NULL 허용 FK 다 — 금액만 제안하고 카테고리를 비운
                // 응답을 AI 가 돌려준 경우에도 카드를 띄울 수 있어야 한다.
                coaching.getProposalCategory() == null
                        ? null
                        : CategoryRefResponse.from(coaching.getProposalCategory()),
                coaching.getProposalGoalType().name(),
                coaching.getProposalGoalAmount(),
                coaching.getProposalGoalCount(),
                coaching.getProposalPeriodDays(),
                coaching.getProposalTitle());
    }
}
