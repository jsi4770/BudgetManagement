package com.back.was.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Access Token 발급·검증. 명세 AUTH-02에 따라 {@code role} 을 클레임으로 담고
 * {@code sub} 에 사용자 PK를 넣는다.
 *
 * <p>Refresh Token 도 서명된 JWT 로 발급한다(명세 AUTH-01 의 쿠키 예시가 JWT 형태).
 * 다만 <b>검증 책임은 여기 없다</b> — Refresh Token 은 서명만으로 유효하다고 볼 수 없고
 * ERD 2장에 따라 DB의 REFRESH_TOKEN 행(해시)과 대조해야 회전·폐기가 성립한다.
 * 그 대조는 AUTH-03·04 를 구현할 때 AuthService 가 맡는다.
 */
@Component
public class JwtProvider {

    /** 명세 AUTH-02: Access Token 의 role 클레임 이름. */
    private static final String ROLE_CLAIM = "role";

    /** HS256 최소 키 길이. 더 짧으면 jjwt 가 WeakKeyException 을 던지는데, 원인이 설정값임을 먼저 알려준다. */
    private static final int MIN_SECRET_BYTES = 32;

    /** jti 생성용. 128비트면 충돌 확률이 무시할 수준이다. */
    private static final int JTI_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtProvider(JwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 이 너무 짧습니다 (HS256 은 UTF-8 %d바이트 이상 필요, 현재 %d바이트)"
                            .formatted(MIN_SECRET_BYTES, secretBytes.length));
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenValiditySeconds = properties.accessTokenValiditySeconds();
        this.refreshTokenValiditySeconds = properties.refreshTokenValiditySeconds();
    }

    public String createAccessToken(long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenValiditySeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Refresh Token 발급. {@code role} 클레임을 담지 않는다 — 재발급 시점에 DB에서
     * 다시 읽어야 권한 변경이 반영되고, 쿠키에 불필요한 정보를 남기지 않는다.
     *
     * <p><b>무작위 {@code jti} 가 필수다.</b> {@code sub}·{@code iat}·{@code exp} 만
     * 담으면 JWT 는 클레임의 결정적 함수이고 시간 단위가 초라서, <b>같은 사용자가 같은
     * 초에 두 번 발급받으면 완전히 동일한 토큰</b>이 나온다. 그러면
     * {@code REFRESH_TOKEN.TOKEN_HASH} UNIQUE 제약에 걸려 로그인이 500이 되고,
     * 기기마다 한 줄이라는 ERD 2장 모델도 성립하지 않는다.
     *
     * <p>ERD 2장이 "충분히 긴 무작위 문자열이라 SHA-256 으로도 안전하다"고 한 전제도
     * 이 {@code jti} 가 만든다 — 서명 키의 비밀성이 아니라 토큰 값 자체의 예측
     * 불가능성이 근거이기 때문이다.
     */
    public String createRefreshToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(newJti())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTokenValiditySeconds)))
                .signWith(key)
                .compact();
    }

    private String newJti() {
        byte[] bytes = new byte[JTI_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 서명·만료를 검증하고 사용자를 복원한다.
     *
     * @throws ExpiredJwtException 만료된 토큰 — 호출자는 {@code TOKEN_EXPIRED} 로 구분해야 한다
     * @throws JwtException        서명 불일치·구조 오류 등 그 밖의 모든 실패
     */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthUser(Long.valueOf(claims.getSubject()), claims.get(ROLE_CLAIM, String.class));
    }

    /** AUTH-01·02 응답의 {@code accessTokenExpiresIn} 에 쓸 값. */
    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    /** refresh_token 쿠키의 {@code Max-Age} 및 REFRESH_TOKEN.EXPIRES_AT 계산에 쓸 값. */
    public long getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }
}
