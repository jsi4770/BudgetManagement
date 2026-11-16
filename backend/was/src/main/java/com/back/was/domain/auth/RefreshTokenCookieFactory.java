package com.back.was.domain.auth;

import com.back.was.global.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 명세 0장 6)의 refresh_token 쿠키를 만든다:
 * {@code HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=2592000}.
 *
 * <p>{@code Path} 가 {@code /api/v1/auth} 인 것은 재발급·로그아웃 요청에만 쿠키가
 * 실려 다른 API 요청에는 아예 붙지 않게 하기 위함이다. 이 값은
 * {@code server.servlet.context-path} 와 맞물려 있어, context-path 를 바꾸면
 * 여기도 같이 바꿔야 한다.
 *
 * <p>{@code SameSite} 는 {@code app.cookie.same-site} 로 정한다(기본 {@code Lax}).
 * 프론트와 API 가 다른 사이트에 배포되면(예: {@code *.vercel.app} ↔
 * {@code *.onrender.com}) 명세 0장 6) 주석대로 {@code None} 이 필요하다 —
 * {@code app.example.com} ↔ {@code api.example.com} 처럼 등록 도메인이 같으면
 * 같은 사이트라 {@code Lax} 로 충분하다. {@code None} 이어도 CSRF 방어는
 * {@code AuthOriginCheckFilter} 의 Origin 검사가 그대로 맡는다.
 */
@Component
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    private static final String PATH = "/api/v1/auth";
    private static final Set<String> SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

    private final JwtProvider jwtProvider;
    private final String sameSite;

    /**
     * 오타(예: {@code none}, {@code Lax })는 부팅 시점에 거절한다. 브라우저는 모르는
     * SameSite 값을 조용히 무시하거나 기본값으로 처리해, 틀려도 로그인 유지가 안 될 뿐
     * 에러가 어디에도 나지 않는다.
     */
    public RefreshTokenCookieFactory(JwtProvider jwtProvider,
                                     @Value("${app.cookie.same-site}") String sameSite) {
        if (!SAME_SITE_VALUES.contains(sameSite)) {
            throw new IllegalArgumentException(
                    "app.cookie.same-site 는 Strict, Lax, None 중 하나여야 합니다: '" + sameSite + "'");
        }
        this.jwtProvider = jwtProvider;
        this.sameSite = sameSite;
    }

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
                .sameSite(sameSite)
                .path(PATH);
    }
}
