package com.back.was.domain.coaching.dto;

import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingMessage;

/**
 * COACH-03 응답 — 이번 턴에 오간 두 메시지와, 제안했다면 그 제안.
 *
 * <p>COACH-01·02 의 {@link CoachingResponse} 와 <b>다른 모양이다</b>. 저쪽은 세션 전체를
 * 복원하고 이쪽은 한 턴만 돌려준다 — 프론트는 이미 그려 둔 대화 아래에 두 줄을 덧붙인다.
 *
 * <p><b>{@code proposal} 은 "이번 응답에서 제안한 경우"만 값이 있다</b>(명세 COACH-03).
 * 저장된 제안을 그대로 비추는 것이 아니라서, 이전 턴에 제안이 저장돼 있어도 이번 턴에
 * 제안하지 않았으면 {@code null} 이다 — 저장된 최신 제안을 돌려주는 것은 COACH-02 의
 * 일이다. {@link #of} 가 {@code proposed} 를 따로 받는 이유가 이것이고, 엔티티만 보고
 * 만들면 두 의미가 섞인다.
 */
public record SendMessageResponse(
        CoachingMessageResponse userMessage,
        AiMessageResponse aiMessage,
        CoachingProposalResponse proposal
) {
    /**
     * @param fallback Gemini 실패로 대체 문구를 쓴 턴인지 — {@code aiMessage.isFallback}
     * @param proposed <b>이번 턴에</b> 제안이 저장됐는지. {@code false} 면 {@code coaching}
     *                 에 이전 제안이 남아 있어도 {@code proposal} 은 {@code null} 이다
     */
    public static SendMessageResponse of(CoachingMessage userMessage, CoachingMessage aiMessage,
                                         boolean fallback, Coaching coaching, boolean proposed) {
        return new SendMessageResponse(
                CoachingMessageResponse.from(userMessage),
                AiMessageResponse.of(aiMessage, fallback),
                proposed ? CoachingProposalResponse.from(coaching) : null);
    }
}
