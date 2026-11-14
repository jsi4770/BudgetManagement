package com.back.was.domain.coaching.dto;

import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingMessage;
import com.back.was.global.support.KstTime;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 코칭 세션 한 건 — <b>COACH-01 과 COACH-02 가 같은 모양</b>이다(명세 COACH-02:
 * "COACH-01과 같은 형식"). 시작 직후의 응답과 재진입 복원 응답이 같은 타입인 것은
 * 우연이 아니라 명세의 요구다. 프론트는 둘을 같은 코드로 그린다.
 *
 * <p>차이는 담기는 값뿐이다: 시작 직후에는 {@code proposal} 이 {@code null} 이고
 * {@code messages} 에 AI 첫 메시지 하나만 들어 있다.
 *
 * <p>{@code id} 가 아니라 <b>{@code coachingId}</b> 다(명세 COACH-01·02 예시). 다른
 * 엔드포인트의 관례({@code TransactionResponse.id})와 다르지만 명세의 키를 따른다.
 *
 * <p>{@code createdAt} 은 <b>코칭 시작 시각</b>이다(명세 COACH-01 "{@code createdAt} =
 * 코칭 시작 시각", ERD 7장). {@code updatedAt} 은 명세의 키 구성에 없어서 내려가지 않는다.
 */
public record CoachingResponse(
        Long coachingId,
        String status,
        OffsetDateTime createdAt,
        CoachingProposalResponse proposal,
        List<CoachingMessageResponse> messages
) {
    /**
     * @param messages {@code createdAt} 오름차순으로 <b>이미 정렬된</b> 대화 (명세
     *                 COACH-02). 여기서 다시 정렬하지 않는다 — 순서는 조회 쿼리의
     *                 책임이고({@code findByCoachingIdOrderByCreatedAtAscIdAsc}) 두 곳에
     *                 두면 어느 쪽이 실제 순서인지 알 수 없게 된다.
     */
    public static CoachingResponse of(Coaching coaching, List<CoachingMessage> messages) {
        return new CoachingResponse(
                coaching.getId(),
                coaching.getStatus().name(),
                KstTime.toOffset(coaching.getCreatedAt()),
                // 제안 전에는 null 이다 — 명세 COACH-01 응답의 "proposal": null.
                // ApiResponse 의 NON_NULL 은 그 레코드의 키에만 걸리므로 이 키는 남는다.
                CoachingProposalResponse.from(coaching),
                messages.stream().map(CoachingMessageResponse::from).toList());
    }
}
