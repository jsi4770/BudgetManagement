package com.back.was.global.security;

import com.back.was.global.exception.ErrorCode;
import com.back.was.global.exception.GlobalExceptionHandler;
import com.back.was.global.exception.security.ApiAccessDeniedHandler;
import com.back.was.global.exception.security.ApiAuthenticationEntryPoint;
import com.back.was.global.exception.security.ApiErrorWriter;
import com.back.was.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 0장 6)의 CSRF 방어(Origin 헤더 검사)를 필터 체인을 통과시켜 검증한다.
 */
@WebMvcTest(controllers = AuthOriginCheckFilterTest.TestController.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        // SecurityConfig 가 JwtProvider 를 요구하므로 JWT 설정도 있어야 컨텍스트가 뜬다.
        "app.jwt.secret=security-chain-test-secret-at-least-32-bytes",
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
        AuthOriginCheckFilterTest.TestController.class
})
class AuthOriginCheckFilterTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String FOREIGN_ORIGIN = "https://evil.example.com";

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("Origin 헤더가 없는 /auth/refresh 는 403 FORBIDDEN으로 거부된다")
    void refreshWithoutOriginIsRejected() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.FORBIDDEN.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Origin 헤더가 없는 /auth/logout 도 403으로 거부된다")
    void logoutWithoutOriginIsRejected() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("프론트 Origin이면 통과한다")
    void frontendOriginPasses() throws Exception {
        mockMvc.perform(post("/auth/refresh").header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post("/auth/logout").header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용 목록에 없는 Origin은 거부된다")
    void foreignOriginIsRejected() throws Exception {
        mockMvc.perform(post("/auth/refresh").header("Origin", FOREIGN_ORIGIN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("preflight(OPTIONS)는 이 필터가 막지 않는다 — POST만 대상")
    void preflightIsNotBlocked() throws Exception {
        mockMvc.perform(options("/auth/refresh")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @Test
    @DisplayName("명세가 지정한 두 경로 밖은 검사하지 않는다 — signup·login은 Origin 없이도 통과")
    void otherAuthPathsAreNotChecked() throws Exception {
        mockMvc.perform(post("/auth/signup"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isOk());
    }

    /**
     * 이 필터가 왜 필요한지를 고정하는 테스트. CORS만으로는 막히지 않는 구멍이
     * 있다는 사실이 전제이므로, 그 전제가 바뀌면 여기서 먼저 깨져야 한다.
     */
    @Nested
    @DisplayName("CORS 단독으로는 부족하다는 전제")
    class CorsAloneIsInsufficient {

        @Test
        @DisplayName("Origin 없는 요청은 CorsFilter를 그대로 통과한다 (필터가 없으면 열린 구멍)")
        void corsFilterLetsMissingOriginThrough() throws Exception {
            // /auth/signup 은 Origin 검사 대상이 아니므로 CORS 단독 동작을 관찰할 수 있다.
            mockMvc.perform(post("/auth/signup"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("허용되지 않은 Origin은 CorsFilter가 403으로 막는다")
        void corsFilterRejectsForeignOrigin() throws Exception {
            mockMvc.perform(post("/auth/signup").header("Origin", FOREIGN_ORIGIN))
                    .andExpect(status().isForbidden());
        }
    }

    @RestController
    static class TestController {

        @PostMapping({"/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout"})
        ApiResponse<String> auth() {
            return ApiResponse.ok("auth");
        }
    }
}
