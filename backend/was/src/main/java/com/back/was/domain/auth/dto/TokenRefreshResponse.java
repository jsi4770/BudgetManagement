package com.back.was.domain.auth.dto;

/**
 * AUTH-03 응답 본문. AUTH-01·02 와 달리 {@code user}·{@code hasBudget} 가 없다 —
 * 페이지 진입마다 호출되는 엔드포인트라 명세가 토큰만 돌려준다.
 *
 * <p>새 Refresh Token 은 바디가 아니라 {@code Set-Cookie} 로 나간다.
 */
public record TokenRefreshResponse(String accessToken, long accessTokenExpiresIn) {
}
