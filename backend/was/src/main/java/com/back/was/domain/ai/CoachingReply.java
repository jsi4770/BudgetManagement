package com.back.was.domain.ai;

/**
 * COACH-03 의 AI 응답 — 문장 하나와, 이번 턴에 목표를 제안했다면 그 제안.
 *
 * <p><b>{@code isFallback} 이 여기 없는 것은 의도다.</b> 이 레코드가 돌아왔다는 것 자체가
 * 성공이고, 대체 응답인지는 <b>누가 만들었는지</b>로 결정된다 — 포트가 실패해서
 * {@code CoachingReplyFallback} 이 만들었으면 {@code true} 다. 그 판단을 서비스가 쥐고
 * 있으므로({@code CoachingService.sendMessage}) 어댑터가 플래그를 잘못 세워 계약을 흔들
 * 여지가 없다.
 *
 * <p>{@code proposal} 이 {@code null} 인 것은 정상이다 — 명세 COACH-03: "이번 응답에서
 * 목표를 제안한 경우만 값 존재". 대화를 더 해 봐야 하는 턴에는 제안이 없다.
 *
 * @param message  사용자에게 보여 줄 문장. 비어 있으면 안 된다
 * @param proposal 이번 턴의 제안, 없으면 {@code null}
 */
public record CoachingReply(
        String message,
        ProposedGoal proposal
) {
}
