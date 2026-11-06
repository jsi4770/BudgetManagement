package com.back.was.domain.auth.dto;

import com.back.was.domain.user.User;

/** AUTH-01·02 응답의 {@code user}. 비밀번호 해시는 절대 담지 않는다. */
public record UserResponse(Long id, String username, String email, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
