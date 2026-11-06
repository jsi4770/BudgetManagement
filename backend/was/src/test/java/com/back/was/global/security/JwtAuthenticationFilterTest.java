package com.back.was.global.security;

import com.back.was.global.exception.ErrorCode;
import com.back.was.global.exception.GlobalExceptionHandler;
import com.back.was.global.exception.security.ApiAccessDeniedHandler;
import com.back.was.global.exception.security.ApiAuthenticationEntryPoint;
import com.back.was.global.exception.security.ApiErrorWriter;
import com.back.was.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bearer 토큰 검증을 필터 체인 전체를 통과시켜 확인한다.
 *
 * <p>가장 중요한 건 {@code TOKEN_EXPIRED} 경로다 — 필터가 요청 속성에 심은
 * {@code ErrorCode}를 EntryPoint가 읽어 응답으로 만드는 핸드셰이크가 실제로
 * 동작하는지는 여기서만 증명된다 (명세 0장 6)-3)의 프론트 재발급 흐름이 이 코드에
 * 의존한다).
 */
@WebMvcTest(controllers = JwtAuthenticationFilterTest.TestController.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "app.jwt.secret=" + JwtAuthenticationFilterTest.SECRET,
        "app.jwt.access-token-validity-seconds=3600",
        "app.jwt.refresh-token-validity-seconds=2592000"
})
@Import({
        SecurityConfig.class,
        JwtProvider.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilterTest.TestController.class
})
class JwtAuthenticationFilterTest {

    static final String SECRET = "integration-test-secret-key-at-least-32-bytes";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("유효한 토큰이면 보호 경로에 접근하고 principal 이 채워진다")
    void validTokenAuthenticates() throws Exception {
        String token = jwtProvider.createAccessToken(7L, "USER");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("7/USER"));
    }

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 토큰은 401 TOKEN_EXPIRED — 프론트의 /auth/refresh 재시도 조건")
    void expiredTokenYieldsTokenExpired() throws Exception {
        // 유효기간을 음수로 준 별도 provider 로 '이미 만료된' 토큰을 만든다. 서명 키는 같다.
        String expired = new JwtProvider(new JwtProperties(SECRET, -60, 2_592_000))
                .createAccessToken(7L, "USER");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.TOKEN_EXPIRED.getMessage()));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 TOKEN_EXPIRED 가 아니라 UNAUTHORIZED")
    void forgedTokenIsUnauthorized() throws Exception {
        String forged = new JwtProvider(new JwtProperties("a-different-secret-key-at-least-32-bytes!", 3600, 2_592_000))
                .createAccessToken(7L, "ADMIN");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("구조가 깨진 토큰도 401 UNAUTHORIZED (원인은 응답에 노출하지 않는다)")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer("garbage")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @Test
    @DisplayName("Bearer 접두사 없는 Authorization 헤더는 무시되고 401")
    void nonBearerHeaderIsIgnored() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Basic abcdef"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("role 클레임이 ADMIN 이면 관리자 API에 접근한다 (명세 ADMIN-01)")
    void adminRoleClaimGrantsAdminApi() throws Exception {
        String token = jwtProvider.createAccessToken(1L, "ADMIN");

        mockMvc.perform(get("/admin/kpi").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("role 클레임이 USER 면 관리자 API는 403 FORBIDDEN")
    void userRoleClaimIsForbiddenOnAdminApi() throws Exception {
        String token = jwtProvider.createAccessToken(2L, "USER");

        mockMvc.perform(get("/admin/kpi").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("깨진 토큰이 와도 공개 경로(POST /auth/signup)는 정상 처리된다 — 필터가 응답을 가로채지 않는 덕분")
    void publicPathWorksDespiteBadToken() throws Exception {
        mockMvc.perform(post("/auth/signup").header(HttpHeaders.AUTHORIZATION, bearer("garbage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("signup"));
    }

    @RestController
    static class TestController {

        /** principal 이 실제로 채워졌는지 보려고 AuthUser 를 그대로 반영한다. */
        @GetMapping("/users/me")
        ApiResponse<String> me(@AuthenticationPrincipal AuthUser user) {
            return ApiResponse.ok(user.id() + "/" + user.role());
        }

        @GetMapping("/admin/kpi")
        ApiResponse<String> kpi() {
            return ApiResponse.ok("kpi");
        }

        /** 명세 0장 1) 인증 ✕ 경로. 토큰이 깨져 있어도 도달해야 한다. */
        @PostMapping("/auth/signup")
        ApiResponse<String> signup() {
            return ApiResponse.ok("signup");
        }
    }
}
