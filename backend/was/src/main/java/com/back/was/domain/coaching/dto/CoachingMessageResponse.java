package com.back.was.domain.coaching.dto;

import com.back.was.domain.coaching.CoachingMessage;
import com.back.was.global.support.KstTime;

import java.time.OffsetDateTime;

/**
 * 대화 한 줄 (명세 COACH-01·02 {@code messages[]}).
 *
 * <p><b>{@code isFallback} 이 없다.</b> 그 키는 COACH-03 의 {@code aiMessage} 에만 붙는다 —
 * 그때 그 자리에서 Gemini 가 실패했다는 뜻이고, 저장되지 않으므로(ERD 8장) 복원된 대화에는
 * 올릴 값이 없다. COACH-03 은 이 타입을 감싸지 않고 자기 응답 안에 그 키까지 포함한 모양을
 * 따로 갖는다({@code NudgeResponse} vs {@code NudgeDetailResponse} 와 같은 판단).
 *
 * <p>{@code createdAt} 은 {@code +09:00} 을 붙여 내려간다 — ERD 는 시간대 없는
 * {@code DATETIME} 으로 저장하지만 명세 0장 2)가 오프셋을 요구한다.
 */
public record CoachingMessageResponse(
        Long id,
        String role,
        String content,
        OffsetDateTime createdAt
) {
    public static CoachingMessageResponse from(CoachingMessage message) {
        return new CoachingMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                KstTime.toOffset(message.getCreatedAt()));
    }
}
