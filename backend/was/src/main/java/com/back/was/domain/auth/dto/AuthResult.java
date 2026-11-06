package com.back.was.domain.auth.dto;

/**
 * 서비스 → 컨트롤러 내부 전달용. Refresh Token 원문은 쿠키로만 나가야 하므로
 * 응답 본문과 분리해 들고 간다.
 *
 * @param <T> 엔드포인트별 응답 본문 ({@link AuthResponse} 또는 {@link TokenRefreshResponse})
 */
public record AuthResult<T>(T response, String refreshToken) {
}
