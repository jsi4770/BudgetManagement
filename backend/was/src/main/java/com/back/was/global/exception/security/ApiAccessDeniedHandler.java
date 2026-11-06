package com.back.was.global.exception.security;

import com.back.was.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인가 실패(403)를 명세 형식으로 내린다. {@code SecurityFilterChain}의
 * {@code exceptionHandling().accessDeniedHandler(...)}에 등록해야 동작한다.
 *
 * <p>여기서 403이 나가는 경우는 명세 0장 5)의 예외 항목 — 관리자 API처럼 기능 자체에
 * 권한이 필요한 경로뿐이다. 남의 리소스 접근은 403이 아니라 404여야 하므로
 * (ID를 훑어 존재 여부를 알아내는 것을 막는다) URL 패턴 권한이 아니라 서비스 계층의
 * 소유권 검사로 처리하고 {@link ErrorCode#NOT_FOUND}를 던진다.
 */
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, ErrorCode.FORBIDDEN);
    }
}
