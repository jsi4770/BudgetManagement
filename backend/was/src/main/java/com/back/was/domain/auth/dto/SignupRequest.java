package com.back.was.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AUTH-01 요청. 규칙은 명세 AUTH-01 표를 그대로 옮겼다.
 *
 * <p>"비밀번호 확인" 일치 검사는 명세대로 프론트에서만 한다.
 */
public record SignupRequest(

        @NotBlank
        @Size(min = 1, max = 50)
        String username,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        /*
         * 8~64자, 영문·숫자·특수문자 각 1개 이상.
         * 상한 64자는 ERD 1장 주석대로 bcrypt 가 72바이트까지만 비교하기 때문이다.
         */
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")
        String password
) {
}
