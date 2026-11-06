package com.back.was.global.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-hs256-at-least-32-bytes";
    private static final String OTHER_SECRET = "another-secret-key-for-hs256-at-least-32b";

    private final JwtProvider provider = providerWith(SECRET, 3600);

    private static JwtProvider providerWith(String secret, long accessTokenValiditySeconds) {
        return new JwtProvider(new JwtProperties(secret, accessTokenValiditySeconds, 2_592_000));
    }

    @Test
    @DisplayName("발급한 토큰에서 사용자 PK와 role 클레임을 복원한다")
    void roundTripsSubjectAndRole() {
        String token = provider.createAccessToken(42L, "ADMIN");

        assertThat(provider.parse(token)).isEqualTo(new AuthUser(42L, "ADMIN"));
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException 으로 구분된다 (TOKEN_EXPIRED 분기의 근거)")
    void expiredTokenThrowsExpiredJwtException() {
        // 유효기간을 음수로 주면 발급 시점에 이미 만료된 토큰이 된다.
        String expired = providerWith(SECRET, -60).createAccessToken(1L, "USER");

        assertThatThrownBy(() -> provider.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거부된다")
    void rejectsTokenSignedWithAnotherKey() {
        String forged = providerWith(OTHER_SECRET, 3600).createAccessToken(1L, "ADMIN");

        assertThatThrownBy(() -> provider.parse(forged))
                .isInstanceOf(JwtException.class)
                .isNotInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("구조가 깨진 토큰은 거부된다")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> provider.parse("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("HS256 최소 길이에 못 미치는 secret 은 생성 시점에 원인을 밝혀 실패한다")
    void rejectsShortSecretEagerly() {
        assertThatThrownBy(() -> providerWith("too-short", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    @Test
    @DisplayName("같은 사용자가 연속 발급받은 Refresh Token 은 서로 다르다")
    void refreshTokensAreUniquePerIssue() {
        // 같은 초에 두 번 발급해도 달라야 한다. jti 가 없으면 JWT 가 클레임의 결정적
        // 함수라서 동일한 문자열이 나오고, REFRESH_TOKEN.TOKEN_HASH UNIQUE 제약에
        // 걸려 두 번째 로그인이 500 이 된다 (ERD 2장: 기기마다 한 줄).
        String first = provider.createRefreshToken(1L);
        String second = provider.createRefreshToken(1L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("accessTokenExpiresIn 으로 쓸 유효기간을 노출한다 (명세 AUTH-02)")
    void exposesAccessTokenValidity() {
        assertThat(providerWith(SECRET, 21_600).getAccessTokenValiditySeconds()).isEqualTo(21_600);
    }
}
