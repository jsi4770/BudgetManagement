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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 필터 체인이 명세 0장 1)의 인증 구분과 6)의 CORS 규칙을 지키는지,
 * 그리고 401·403이 명세 0장 3) 형식으로 나오는지 확인한다.
 *
 * <p>필터를 끄지 않는다 ({@code addFilters} 기본값 true) — 이 테스트의 대상이
 * 바로 필터 체인이다. {@code @WebMvcTest}는 JPA 자동 구성을 로드하지 않으므로
 * datasource 없이 돈다.
 *
 * <p>MockMvc 요청 경로에 {@code /api/v1}을 붙이지 않는다: Security matcher와
 * 핸들러 매핑 모두 context path를 제외한 경로를 본다.
 */
@WebMvcTest(controllers = SecurityConfigTest.TestController.class, properties = {
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
        SecurityConfigTest.TestController.class
})
class SecurityConfigTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("미인증 요청은 401 UNAUTHORIZED를 명세 JSON 형식으로 받는다")
    void unauthenticatedRequestGetsSpecShaped401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("401 응답은 JSON이며 한국어 메시지가 깨지지 않는다")
    void unauthorizedResponseIsJsonWithIntactKorean() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @Test
    @DisplayName("기본 HTTP Basic이 꺼져 있어 WWW-Authenticate 팝업이 뜨지 않는다")
    void httpBasicIsDisabled() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("명세 0장 1) 인증 ✕ 경로 4개는 토큰 없이 통과한다")
    void publicAuthPathsArePermitted() throws Exception {
        mockMvc.perform(post("/auth/signup"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isOk());
        // refresh·logout 은 인증은 면제되지만 명세 0장 6)의 Origin 검사를 받는다
        // (AuthOriginCheckFilter). 여기서 확인하려는 건 401이 아니라는 것뿐이고,
        // Origin 규칙 자체는 AuthOriginCheckFilterTest 가 다룬다.
        mockMvc.perform(post("/auth/refresh").header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/logout").header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일반 사용자(USER)의 관리자 API 호출은 403 FORBIDDEN (명세 ADMIN-01)")
    @WithMockUser(roles = "USER")
    void userCallingAdminApiGetsSpecShaped403() throws Exception {
        mockMvc.perform(get("/admin/kpi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.FORBIDDEN.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN은 관리자 API에 접근할 수 있다")
    @WithMockUser(roles = "ADMIN")
    void adminCanCallAdminApi() throws Exception {
        mockMvc.perform(get("/admin/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("인증된 사용자는 보호 경로에 접근할 수 있다")
    @WithMockUser
    void authenticatedUserReachesProtectedPath() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("프론트 도메인 preflight는 credentials 허용으로 통과한다 (명세 0장 6)")
    void preflightFromFrontendOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/users/me")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("허용 목록에 없는 Origin의 preflight는 거부된다 (Allow-Origin 에 * 불가)")
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/users/me")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @RestController
    static class TestController {

        @GetMapping("/users/me")
        ApiResponse<String> me() {
            return ApiResponse.ok("me");
        }

        @GetMapping("/admin/kpi")
        ApiResponse<String> kpi() {
            return ApiResponse.ok("kpi");
        }

        @PostMapping({"/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout"})
        ApiResponse<String> auth() {
            return ApiResponse.ok("auth");
        }
    }
}
