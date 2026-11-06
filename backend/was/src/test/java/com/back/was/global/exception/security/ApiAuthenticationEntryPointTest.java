package com.back.was.global.exception.security;

import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 401의 구체적 사유 분기를 검증한다.
 *
 * <p>명세 0장 6)-3)의 프론트는 {@code TOKEN_EXPIRED}를 보고 {@code /auth/refresh}를
 * 1회 호출한 뒤 원래 요청을 재시도한다. 만료를 {@code UNAUTHORIZED}로 내리면 그
 * 재발급 흐름이 죽고 사용자가 그냥 로그아웃되므로, 이 분기는 기능 요건이다.
 *
 * <p>필터 체인을 띄우지 않고 직접 호출한다 — JWT 필터가 아직 없어서
 * {@code TOKEN_EXPIRED} 경로를 통합 테스트로 만들 수 없다.
 */
class ApiAuthenticationEntryPointTest {

    private final ApiAuthenticationEntryPoint entryPoint =
            new ApiAuthenticationEntryPoint(new ApiErrorWriter(new ObjectMapper()));

    @Test
    @DisplayName("JWT 필터가 심은 ErrorCode가 있으면 그 코드로 401을 내린다")
    void usesErrorCodeStashedByFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("expired"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"TOKEN_EXPIRED\"");
    }

    @Test
    @DisplayName("심긴 ErrorCode가 없으면 UNAUTHORIZED로 떨어진다")
    void fallsBackToUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("한국어 메시지가 UTF-8로 깨지지 않고 나간다")
    void writesKoreanMessageAsUtf8() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        assertThat(response.getContentAsString()).contains(ErrorCode.UNAUTHORIZED.getMessage());
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("실패 응답에는 성공 키(data)가 섞이지 않는다")
    void failureBodyHasNoDataKey() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .doesNotContain("\"data\"");
    }
}
