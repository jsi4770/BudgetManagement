package com.back.was.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-02 요청.
 *
 * <p>형식 검증을 느슨하게 둔다 — 이메일 형식까지 검사해 400을 주면 "그 이메일은
 * 형식조차 틀렸다"와 "비밀번호가 틀렸다"가 구분돼 계정 존재 여부 추측에 쓰일 수 있다.
 * 명세 AUTH-02 의 오류도 {@code 401 INVALID_CREDENTIALS} 하나뿐이다.
 */
public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String password
) {
}
