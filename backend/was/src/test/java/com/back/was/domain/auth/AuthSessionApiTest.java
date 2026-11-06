package com.back.was.domain.auth;

import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUTH-03 토큰 재발급 · AUTH-04 로그아웃.
 *
 * <p>두 경로는 {@code AuthOriginCheckFilter} 가 지키므로 모든 요청에
 * {@code Origin} 헤더가 필요하다 (명세 0장 6) CSRF 방어). 헤더를 빼면 핸들러에
 * 닿기도 전에 403 이다 — 그 동작도 아래에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSessionApiTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final String EMAIL = "hong@email.com";
    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    TokenHasher tokenHasher;

    private Cookie refreshCookie;

    @BeforeEach
    void signUpAndCaptureCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        refreshCookie = result.getResponse().getCookie(RefreshTokenCookieFactory.COOKIE_NAME);
        assertThat(refreshCookie).isNotNull();
    }

    private MvcResult refresh(Cookie cookie) throws Exception {
        var request = post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN);
        if (cookie != null) {
            request = request.cookie(cookie);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    @DisplayName("AUTH-03: 200 과 함께 accessToken·accessTokenExpiresIn 만 준다")
    void refreshReturnsOnlyTokens() throws Exception {
        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(21600))
                // 명세 AUTH-03 응답에는 user·hasBudget 이 없다.
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andExpect(jsonPath("$.data.hasBudget").doesNotExist());
    }

    @Test
    @DisplayName("AUTH-03: 새 refresh_token 쿠키가 나가고 이전 값과 다르다 (rotation)")
    void refreshRotatesCookie() throws Exception {
        MvcResult result = refresh(refreshCookie);

        Cookie rotated = result.getResponse().getCookie(RefreshTokenCookieFactory.COOKIE_NAME);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(refreshCookie.getValue());
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("Max-Age=2592000")
                .contains("Path=/api/v1/auth")
                .contains("HttpOnly");
    }

    @Test
    @DisplayName("AUTH-03: ERD 2장 회전 — 기존 행을 삭제하고 새 행을 만든다 (행 수 유지)")
    void refreshReplacesStoredRow() throws Exception {
        String oldHash = tokenHasher.hash(refreshCookie.getValue());
        assertThat(refreshTokenRepository.findByTokenHash(oldHash)).isPresent();

        MvcResult result = refresh(refreshCookie);
        String newValue = result.getResponse().getCookie(RefreshTokenCookieFactory.COOKIE_NAME).getValue();

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(oldHash)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(tokenHasher.hash(newValue))).isPresent();
    }

    @Test
    @DisplayName("AUTH-03: 회전된 옛 토큰은 재사용할 수 없다 → 401 INVALID_REFRESH_TOKEN")
    void rotatedTokenCannotBeReused() throws Exception {
        refresh(refreshCookie);

        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("AUTH-03: 쿠키가 없으면 500이 아니라 401 INVALID_REFRESH_TOKEN")
    void missingCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("AUTH-03: 위조된 쿠키 값은 401 INVALID_REFRESH_TOKEN")
    void unknownCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(RefreshTokenCookieFactory.COOKIE_NAME, "forged-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("AUTH-03: 만료된 행은 401 이고 그 행은 정리된다")
    void expiredRowIsRejectedAndCleanedUp() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        refreshTokenRepository.save(RefreshToken.issue(
                user, tokenHasher.hash("expired-token"), LocalDateTime.now().minusDays(1)));

        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(RefreshTokenCookieFactory.COOKIE_NAME, "expired-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        assertThat(refreshTokenRepository.findByTokenHash(tokenHasher.hash("expired-token"))).isEmpty();
    }

    @Test
    @DisplayName("AUTH-03: Origin 헤더가 없으면 CSRF 필터가 403으로 먼저 막는다")
    void refreshWithoutOriginIsBlockedByCsrfFilter() throws Exception {
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("AUTH-04: 204 와 함께 Max-Age=0 쿠키로 브라우저 쿠키를 지운다")
    void logoutClearsCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;")
                .contains("Max-Age=0")
                .contains("Path=/api/v1/auth")
                .contains("HttpOnly");
    }

    @Test
    @DisplayName("AUTH-04: 해당 기기의 행만 지우고 다른 기기 로그인은 유지한다")
    void logoutDeletesOnlyThatDevicesRow() throws Exception {
        // 두 번째 기기 로그인
        MvcResult otherDevice = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie otherCookie = otherDevice.getResponse().getCookie(RefreshTokenCookieFactory.COOKIE_NAME);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(tokenHasher.hash(refreshCookie.getValue()))).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(tokenHasher.hash(otherCookie.getValue()))).isPresent();
    }

    @Test
    @DisplayName("AUTH-04: 쿠키가 없거나 이미 폐기됐어도 204 (멱등) — 명세에 오류 코드가 없다")
    void logoutIsIdempotent() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(RefreshTokenCookieFactory.COOKIE_NAME, "already-gone")))
                .andExpect(status().isNoContent());

        // 두 번 호출해도 유효한 행은 그대로다.
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AUTH-04: 로그아웃한 토큰으로는 재발급할 수 없다")
    void refreshFailsAfterLogout() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }
}
