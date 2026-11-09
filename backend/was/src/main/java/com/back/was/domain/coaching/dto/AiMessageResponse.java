package com.back.was.domain.coaching.dto;

import com.back.was.domain.coaching.CoachingMessage;
import com.back.was.global.support.KstTime;

import java.time.OffsetDateTime;

/**
 * COACH-03 의 {@code aiMessage} — {@link CoachingMessageResponse} 의 네 필드에
 * <b>{@code isFallback}</b> 이 하나 더 붙은 모양이다 (명세 COACH-03).
 *
 * <p>감싸지 않고 필드를 다시 적는 이유는 {@code NudgeDetailResponse} 와 같다: 명세의 JSON
 * 이 평평하고, {@code @JsonUnwrapped} 로 맞추면 응답 모양이 애너테이션에 매달린다.
 *
 * <p><b>{@code isFallback} 은 저장되지 않는다</b>(ERD 8장). 그래서 엔티티에서 읽을 수 없고
 * 서비스가 "포트가 실패했는가"를 그대로 넘겨 준다 — 같은 메시지를 COACH-02 로 다시 읽으면
 * 이 키는 없다({@code CoachingMessageResponse} 에는 없는 필드다). 다시 열었을 때 "그때 AI
 * 가 쉬고 있었다"를 보여 줄 화면이 없기 때문이다.
 *
 * <p>프론트는 {@code true} 일 때 "AI 연결이 원활하지 않아요"를 안내한다 (명세 COACH-03 /
 * SFR-026).
 */
public record AiMessageResponse(
        Long id,
        String role,
        String content,
        OffsetDateTime createdAt,
        boolean isFallback
) {
    public static AiMessageResponse of(CoachingMessage message, boolean fallback) {
        return new AiMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                KstTime.toOffset(message.getCreatedAt()),
                fallback);
    }
}
