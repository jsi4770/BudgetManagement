package com.back.was.domain.auth;

import com.back.was.domain.auth.dto.AuthResponse;
import com.back.was.domain.auth.dto.AuthResult;
import com.back.was.domain.auth.dto.LoginRequest;
import com.back.was.domain.auth.dto.SignupRequest;
import com.back.was.domain.auth.dto.TokenRefreshResponse;
import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "1. 인증 (AUTH)", description = """
        회원가입·로그인·재발급·로그아웃. **이 네 개만 토큰 없이 호출합니다** (명세 0장 1)).

        재발급·로그아웃은 Bearer 가 아니라 `refresh_token` 쿠키로 인증하고, CSRF 방어 때문에
        **신뢰된 `Origin` 헤더가 없으면 컨트롤러에 닿기 전에 403** 입니다 (0장 6)). 그래서 이 둘은
        Swagger UI 에서 바로 시험하기 어렵습니다.""")
@SecurityRequirements
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @Operation(summary = "AUTH-01 회원가입", description = """
            가입과 동시에 로그인 상태가 됩니다 — `201` 과 함께 액세스 토큰, 그리고 `refresh_token` 쿠키를 내려줍니다.

            응답의 `hasBudget` 이 `false` 면 예산을 한 번도 세우지 않은 사용자이니 초기 설정 화면으로 보내세요.

            **실패**: `400 VALIDATION_ERROR`(형식 오류), `409 EMAIL_DUPLICATED`(이미 가입된 이메일)""")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@RequestBody @Valid SignupRequest request) {
        return respond(authService.signup(request), HttpStatus.CREATED);
    }

    @Operation(summary = "AUTH-02 로그인", description = """
            응답 형식은 AUTH-01 과 같습니다. `accessToken` 유효 시간은 6시간이고,
            `refresh_token` 쿠키는 30일입니다.

            **실패**: `401 INVALID_CREDENTIALS` — 없는 이메일과 틀린 비밀번호를 **구분하지 않습니다**
            (어느 이메일이 가입돼 있는지 알려주지 않기 위해).""")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody @Valid LoginRequest request) {
        return respond(authService.login(request), HttpStatus.OK);
    }

    /**
     * 쿠키를 {@code required = false} 로 받는다. {@code true} 면 쿠키가 없을 때
     * {@code MissingRequestCookieException} 이 나고 매핑되지 않은 예외로 500 이 되는데,
     * 명세는 이 경우도 {@code 401 INVALID_REFRESH_TOKEN} 이다 — 판단은 서비스가 한다.
     */
    @Operation(summary = "AUTH-03 토큰 재발급", description = """
            본문이 없습니다. 브라우저가 `refresh_token` 쿠키를 자동으로 보냅니다.

            응답에는 `accessToken` 과 `accessTokenExpiresIn` 만 있습니다 — 사용자 정보는 없습니다.
            호출할 때마다 **쿠키가 새 토큰으로 교체**되므로(회전) 이전 토큰은 그 즉시 무효입니다.
            두 탭이 동시에 재발급하면 늦은 쪽은 401 을 받고 다시 로그인해야 합니다.

            **실패**: `401 INVALID_REFRESH_TOKEN`(쿠키 없음·만료·이미 교체됨),
            `403 FORBIDDEN`(신뢰된 `Origin` 헤더 없음 — 0장 6))""")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        return respond(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @Operation(summary = "AUTH-04 로그아웃", description = """
            본문 없는 `204` 와 함께 쿠키를 만료시킵니다.

            **멱등합니다** — 쿠키가 없거나 이미 무효인 토큰이어도 204 입니다. 실패를 알리면
            "그 토큰이 유효했다"를 알려주는 셈이고, 브라우저의 쿠키는 어느 경우든 치워야 합니다.

            **실패**: `403 FORBIDDEN`(신뢰된 `Origin` 헤더 없음)""")
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
