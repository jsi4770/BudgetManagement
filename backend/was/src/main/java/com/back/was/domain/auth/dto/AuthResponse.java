package com.back.was.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AUTH-01·02 응답 본문.
 *
 * <p>Refresh Token 은 여기 없다 — 명세 0장 6)에 따라 {@code Set-Cookie} 로만 나간다.
 *
 * <p>{@code hasBudget} 은 AUTH-02 에만 있는 필드다. AUTH-01 에서는 null 로 두어
 * 직렬화에서 빠진다 ({@code NON_NULL}) — 가입 직후에는 프론트가 무조건 초기 설정
 * 화면으로 가므로 명세 AUTH-01 응답에 이 키가 없다.
 *
 * @param accessTokenExpiresIn Access Token 유효 시간(초). 명세 AUTH-02 기준 21,600
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UserResponse user,
        String accessToken,
        long accessTokenExpiresIn,
        Boolean hasBudget
) {
    public static AuthResponse forSignup(UserResponse user, String accessToken, long expiresIn) {
        return new AuthResponse(user, accessToken, expiresIn, null);
    }

    public static AuthResponse forLogin(UserResponse user, String accessToken, long expiresIn, boolean hasBudget) {
        return new AuthResponse(user, accessToken, expiresIn, hasBudget);
    }
}
