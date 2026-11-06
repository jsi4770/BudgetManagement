package com.back.was.domain.auth;

import com.back.was.domain.auth.dto.AuthResponse;
import com.back.was.domain.auth.dto.AuthResult;
import com.back.was.domain.auth.dto.LoginRequest;
import com.back.was.domain.auth.dto.SignupRequest;
import com.back.was.domain.auth.dto.TokenRefreshResponse;
import com.back.was.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 명세 2장 인증(AUTH). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    /** AUTH-01 회원가입 — 가입 즉시 자동 로그인이라 {@code 201} 과 함께 토큰이 나간다. */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@RequestBody @Valid SignupRequest request) {
        return respond(authService.signup(request), HttpStatus.CREATED);
    }

    /** AUTH-02 로그인 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody @Valid LoginRequest request) {
        return respond(authService.login(request), HttpStatus.OK);
    }

    /**
     * AUTH-03 토큰 재발급. 바디는 없고 브라우저가 refresh_token 쿠키를 자동 전송한다.
     *
     * <p>{@code required = false} 로 받는다. {@code true} 면 쿠키가 없을 때
     * {@code MissingRequestCookieException} 이 나고 매핑되지 않은 예외로 500이 되는데,
     * 명세는 이 경우도 {@code 401 INVALID_REFRESH_TOKEN} 이다 — 판단은 서비스가 한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        return respond(authService.refresh(refreshToken), HttpStatus.OK);
    }

    /**
     * AUTH-04 로그아웃. 본문 없는 {@code 204} 와 함께 쿠키를 만료시킨다.
     *
     * <p>쿠키 삭제는 토큰이 유효했는지와 무관하게 항상 내보낸다 — 서버에 지울 행이
     * 없더라도 브라우저에 남은 쿠키는 치워야 한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
                .build();
    }

    /** Refresh Token 은 응답 바디가 아니라 Set-Cookie 로만 내보낸다 (명세 0장 6). */
    private <T> ResponseEntity<ApiResponse<T>> respond(AuthResult<T> result, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }
}
