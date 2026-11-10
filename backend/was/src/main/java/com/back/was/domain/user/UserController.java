package com.back.was.domain.user;

import com.back.was.domain.user.dto.MeResponse;
import com.back.was.domain.user.dto.UpdateMeRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. 사용자 (USER)", description = """
        내 정보 조회·수정. 대상은 항상 토큰의 주인이라 경로에 사용자 ID 가 없습니다.""")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "USER-01 내 정보 조회", description = """
            마이페이지 상단. `createdAt` 은 가입일이고 KST 오프셋(`+09:00`)이 붙습니다.""")
    @GetMapping("/me")
    public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(userService.getMe(authUser.id()));
    }

    @Operation(summary = "USER-02 내 정보 수정", description = """
            **보낸 필드만 수정합니다.** 바꿀 수 있는 것은 `username` 하나입니다 —
            이메일은 로그인 ID 라서, 비밀번호는 현재 비밀번호 확인이 필요해서 여기서 다루지 않습니다.

            `email`·`role` 같은 키를 함께 보내도 **무시**됩니다.

            **실패**: `400 VALIDATION_ERROR` — 빈 문자열이나 공백만 보낸 경우""")
    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateMe(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestBody @Valid UpdateMeRequest request) {
        return ApiResponse.ok(userService.updateMe(authUser.id(), request));
    }
}
