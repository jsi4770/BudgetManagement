package com.back.was.domain.auth;

import com.back.was.domain.user.Role;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUTH-01 회원가입 · AUTH-02 로그인을 전체 스택(H2 + security 체인 + 예외 핸들러)으로
 * 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    private static final String EMAIL = "hong@email.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TokenHasher tokenHasher;

    private static String signupBody(String username, String email, String password) {
        return """
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, password);
    }

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private MvcResult signup(String email) throws Exception {
        return mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("홍길동", email, PASSWORD)))
                .andReturn();
    }

    @Test
    @DisplayName("AUTH-01: 201과 함께 user·accessToken·accessTokenExpiresIn 을 준다")
    void signupReturnsCreatedWithTokens() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("홍길동", EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.id").isNumber())
                .andExpect(jsonPath("$.data.user.username").value("홍길동"))
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(21600))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("AUTH-01: 명세 AUTH-01 응답에 hasBudget 키가 없다")
    void signupResponseOmitsHasBudget() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("홍길동", EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hasBudget").doesNotExist());
    }

    @Test
    @DisplayName("AUTH-01: Refresh Token 은 바디에 없고 명세 0장 6) 속성의 쿠키로만 나간다")
    void refreshTokenGoesOutOnlyAsCookie() throws Exception {
        MvcResult result = signup(EMAIL);

        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith("refresh_token=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=2592000");
    }

    @Test
    @DisplayName("AUTH-01: 비밀번호는 bcrypt 해시로만 저장된다")
    void passwordIsStoredHashed() throws Exception {
        signup(EMAIL);

        User saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("AUTH-01: REFRESH_TOKEN 행 1개가 원문이 아닌 SHA-256 해시로 저장된다 (ERD 2장)")
    void refreshTokenRowStoresHashOnly() throws Exception {
        MvcResult result = signup(EMAIL);
        String rawToken = result.getResponse().getHeader(HttpHeaders.SET_COOKIE)
                .substring("refresh_token=".length())
                .split(";")[0];

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        RefreshToken row = refreshTokenRepository.findAll().getFirst();
        assertThat(row.getTokenHash())
                .isNotEqualTo(rawToken)
                .isEqualTo(tokenHasher.hash(rawToken))
                .hasSize(64);
        assertThat(row.getExpiresAt()).isAfter(row.getCreatedAt());
    }

    @Test
    @DisplayName("AUTH-01: 이미 가입된 이메일은 409 EMAIL_DUPLICATED")
    void duplicatedEmailIsConflict() throws Exception {
        signup(EMAIL);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("다른사람", EMAIL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("AUTH-01: 비밀번호 규칙(영문·숫자·특수문자 각 1개 이상) 위반은 400 VALIDATION_ERROR")
    void weakPasswordIsValidationError() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("홍길동", EMAIL, "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("AUTH-01: 필수값 누락은 reason REQUIRED 로 나간다")
    void missingFieldsAreReportedAsRequired() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'email')].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("AUTH-02: 로그인은 200과 hasBudget 을 준다 (예산 없으면 false)")
    void loginReturnsHasBudgetFalseWithoutBudget() throws Exception {
        signup(EMAIL);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(21600))
                .andExpect(jsonPath("$.data.hasBudget").value(false));
    }

    @Test
    @DisplayName("AUTH-02: 비밀번호가 틀리면 401 INVALID_CREDENTIALS")
    void wrongPasswordIsInvalidCredentials() throws Exception {
        signup(EMAIL);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "wrongPassword1!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AUTH-02: 없는 이메일도 같은 INVALID_CREDENTIALS — 가입 여부를 알려주지 않는다")
    void unknownEmailIsIndistinguishable() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@email.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AUTH-02: 로그인마다 REFRESH_TOKEN 행이 하나씩 늘어난다 (기기별 1행, ERD 2장)")
    void eachLoginAddsRefreshTokenRow() throws Exception {
        signup(EMAIL);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk());

        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("발급받은 Access Token 이 실제로 인증에 쓰인다 — 토큰 없으면 401, 있으면 role 이 적용된다")
    void issuedAccessTokenAuthenticatesEndToEnd() throws Exception {
        MvcResult result = signup(EMAIL);
        String accessToken = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.accessToken");

        // 토큰 없이 관리자 API → 401 (인증 자체가 안 됨)
        mockMvc.perform(get("/admin/kpi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // USER 토큰으로 관리자 API → 403 (인증은 됐고 권한이 없음)
        mockMvc.perform(get("/admin/kpi").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
