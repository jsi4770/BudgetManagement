package com.back.was.domain.user;

import com.back.was.domain.user.dto.MeResponse;
import com.back.was.domain.user.dto.UpdateMeRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 3장 회원(USER). 두 엔드포인트 모두 인증 필요(명세 1장)이고,
 * 대상은 항상 토큰의 주인이라 경로에 사용자 ID 가 없다.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** USER-01 내 정보 조회 */
    @GetMapping("/me")
    public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(userService.getMe(authUser.id()));
    }

    /** USER-02 내 정보 수정 */
    @PatchMapping("/me")
    public ApiResponse<MeResponse> updateMe(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestBody @Valid UpdateMeRequest request) {
        return ApiResponse.ok(userService.updateMe(authUser.id(), request));
    }
}
