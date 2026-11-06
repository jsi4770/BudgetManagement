package com.back.was.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 유효 기간 기본값은 명세 0장 6)·AUTH-02의 값이다
 * (Access 6시간 = 21,600초, Refresh 30일 = 2,592,000초).
 *
 * @param secret                      HS256 서명 키. UTF-8 32바이트(256비트) 이상이어야 한다.
 *                                    운영에서는 반드시 환경변수로 덮어쓴다.
 * @param accessTokenValiditySeconds  AUTH-01·02 응답의 {@code accessTokenExpiresIn} 과 같은 값
 * @param refreshTokenValiditySeconds refresh_token 쿠키 {@code Max-Age} 와 같은 값
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds,
        long refreshTokenValiditySeconds
) {
}
