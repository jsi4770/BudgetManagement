package com.back.was.global.exception.security;

import com.back.was.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 실패(401)를 명세 형식으로 내린다. {@code SecurityFilterChain}의
 * {@code exceptionHandling().authenticationEntryPoint(...)}에 등록해야 동작한다.
 *
 * <p>{@link AuthenticationException}만으로는 "토큰 없음"과 "토큰 만료"가 구분되지
 * 않는다. 명세 0장 6)-3)의 프론트 재발급 인터셉터는 {@code TOKEN_EXPIRED}를 보고
 * {@code /auth/refresh}를 호출하므로 이 구분이 기능 요건이다. 그래서 JWT 검증
 * 필터가 만료를 감지하면 {@link #ERROR_CODE_ATTRIBUTE}에 {@link ErrorCode}를 심고,
 * 여기서 그것을 읽어 분기한다. 심긴 값이 없으면 {@code UNAUTHORIZED}로 떨어진다.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** JWT 필터가 401의 구체적 사유를 전달하는 요청 속성 키. */
    public static final String ERROR_CODE_ATTRIBUTE = "com.back.was.authErrorCode";

    private final ApiErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        errorWriter.write(response, resolve(request));
    }

    private ErrorCode resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        return attribute instanceof ErrorCode errorCode ? errorCode : ErrorCode.UNAUTHORIZED;
    }
}
