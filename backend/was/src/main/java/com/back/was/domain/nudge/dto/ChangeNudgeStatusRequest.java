package com.back.was.domain.nudge.dto;

import jakarta.validation.constraints.NotNull;

/**
 * NDG-03 요청. 명세의 본문은 {@code {"status": "DISMISSED"}} 하나뿐이다.
 *
 * <p>PATCH 지만 "보낸 필드만 수정"(USER-02·TX-05)과 달리 {@code status} 는 <b>필수</b>다 —
 * 바꿀 수 있는 필드가 그것뿐이어서, 빼고 보내면 요청 자체가 의미가 없다.
 *
 * <p>값의 범위는 {@link NudgeReaction} 이 타입으로 막는다.
 */
public record ChangeNudgeStatusRequest(

        @NotNull
        NudgeReaction status
) {
}
