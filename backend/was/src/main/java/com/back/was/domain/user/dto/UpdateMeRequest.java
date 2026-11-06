package com.back.was.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * USER-02 요청. 명세대로 <b>보낸 필드만</b> 수정하므로 모든 필드가 선택이고,
 * {@code null} 은 "바꾸지 않음"을 뜻한다.
 *
 * <p>명세 USER-02 가 문서화한 수정 가능 필드는 {@code username} 뿐이다. 이메일은
 * 로그인 ID 이자 UNIQUE 이고 비밀번호 변경은 현재 비밀번호 확인이 필요한데, 둘 다
 * 명세에 규칙이 없어 여기서 다루지 않는다.
 *
 * <p>{@code @NotBlank} 를 쓸 수 없다 — 그러면 "필드를 보내지 않음"까지 거부된다.
 * 대신 길이 하한과 공백-only 차단을 따로 건다. 두 제약 모두 {@code null} 은 통과시킨다.
 */
public record UpdateMeRequest(

        @Size(min = 1, max = 50)
        @Pattern(regexp = ".*\\S.*")
        String username
) {
    public boolean hasUsername() {
        return username != null;
    }
}
