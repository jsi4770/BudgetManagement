package com.back.was.domain.coaching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * COACH-03 요청. 명세 COACH-03 표: {@code content} 는 필수, <b>1~500자</b>.
 *
 * <p>"1자 이상"은 {@link NotBlank} 가 맡는다 — {@code @Size(min = 1)} 로는 {@code "   "}
 * 를 통과시키는데, 공백만 보낸 메시지는 AI 에게 넘길 맥락도 화면에 보여 줄 내용도 아니다.
 * {@code NotBlank} 는 {@code null}·{@code ""}·공백을 모두 막고
 * {@code GlobalExceptionHandler} 가 그 셋을 {@code reason: REQUIRED} 로 바꾼다
 * (USER-02 의 PATCH 와 반대 상황이다 — 저쪽은 "안 보냄"을 허용해야 해서
 * {@code NotBlank} 를 쓸 수 없었다).
 */
public record SendMessageRequest(

        @NotBlank
        @Size(max = 500)
        String content
) {
}
