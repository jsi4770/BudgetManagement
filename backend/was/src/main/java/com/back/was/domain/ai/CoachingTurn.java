package com.back.was.domain.ai;

import com.back.was.domain.coaching.MessageRole;

/**
 * 대화 한 줄 — Gemini 에게 넘길 맥락이다 (ERD 8장: 메시지를 저장하는 이유 중 하나가
 * "대화 중 AI 에게 이전 맥락을 넘기기" 다).
 *
 * <p>{@code CoachingMessage} 엔티티를 그대로 넘기지 않는 이유는 다른 커맨드들과 같다 —
 * AI 어댑터는 DB·JPA 를 몰라야 한다. 여기 담기는 것은 모델이 실제로 읽어야 하는 두
 * 값뿐이고, {@code id} 나 {@code createdAt} 은 들어가지 않는다.
 */
public record CoachingTurn(
        MessageRole role,
        String content
) {
}
