package com.back.was.domain.auth;

import com.back.was.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 명세 0장 6)의 refresh_token 쿠키를 만든다:
 * {@code HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=2592000}.
 *
 * <p>{@code Path} 가 {@code /api/v1/auth} 인 것은 재발급·로그아웃 요청에만 쿠키가
 * 실려 다른 API 요청에는 아예 붙지 않게 하기 위함이다. 이 값은
 * {@code server.servlet.context-path} 와 맞물려 있어, context-path 를 바꾸면
 * 여기도 같이 바꿔야 한다.
 *
 * <p>프론트와 API 가 다른 사이트에 배포되면(예: {@code *.vercel.app} ↔
 * {@code *.onrender.com}) 명세 0장 6) 주석대로 {@code SameSite=None} 이 필요하다.
 * 그 경우 {@link #SAME_SITE} 를 설정값으로 빼야 한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    private static final String PATH = "/api/v1/auth";
    private static final String SAME_SITE = "Lax";

    private final JwtProvider jwtProvider;

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken)
                .maxAge(Duration.ofSeconds(jwtProvider.getRefreshTokenValiditySeconds()))
                .build();
    }

    /** AUTH-04 로그아웃: 같은 속성에 빈 값·{@code Max-Age=0} 으로 덮어써야 브라우저가 지운다. */
    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
