package com.back.was.domain.user.dto;

import com.back.was.domain.user.User;
import com.back.was.global.support.KstTime;

import java.time.OffsetDateTime;

/**
 * USER-01·02 응답. 두 엔드포인트가 같은 모양이다 (명세 USER-02 "USER-01과 동일").
 *
 * <p>AUTH-01·02 의 {@code user} 객체({@code domain.auth.dto.UserResponse})와 달리
 * {@code createdAt} 이 있다 — 명세상 가입·로그인 응답에는 그 필드가 없어서 타입을
 * 공유하지 않는다.
 */
public record MeResponse(
        Long id,
        String username,
        String email,
        String role,
        OffsetDateTime createdAt
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                KstTime.toOffset(user.getCreatedAt()));
    }
}
