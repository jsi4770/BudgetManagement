package com.back.was.domain.dev.dto;

import com.back.was.domain.dev.DevChallengeScenario;

/**
 * DEV-02 요청. 필드가 하나뿐이고 <b>선택</b>이다 (명세 DEV-02).
 *
 * <p>{@code scenario} 가 없으면 "기간 종료 · 결과 보기" — 거래를 추가하지 않고 현재 기록
 * 그대로 판정한다. 그래서 본문 자체가 없어도 되고, {@link #emptyIfNull} 이
 * {@code StartCoachingRequest} 와 같은 역할을 한다.
 *
 * <p>검증 애너테이션이 없다. {@code SUCCESS}/{@code FAILED} 가 아닌 값은 Jackson 의 enum
 * 역직렬화가 실패해 400 {@code VALIDATION_ERROR} 가 된다.
 */
public record CompleteChallengeRequest(
        DevChallengeScenario scenario
) {
    public static CompleteChallengeRequest emptyIfNull(CompleteChallengeRequest request) {
        return request == null ? new CompleteChallengeRequest(null) : request;
    }
}
