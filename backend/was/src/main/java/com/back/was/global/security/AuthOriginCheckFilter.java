package com.back.was.global.security;

import com.back.was.global.exception.ErrorCode;
import com.back.was.global.exception.security.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 명세 0장 6) 「CSRF 방어: {@code /auth/refresh}, {@code /auth/logout} 요청의
 * {@code Origin} 헤더가 프론트 도메인인지 검사」.
 *
 * <p><b>왜 CORS로 충분하지 않은가</b> — 실측한 결과는 이렇다:
 * <ul>
 *   <li>허용 목록에 없는 {@code Origin} → Spring {@code CorsFilter}가 이미 403으로 차단</li>
 *   <li>{@code Origin} 헤더 <b>없음</b> → {@code CorsUtils}가 CORS 요청으로 보지 않아 <b>그대로 통과</b></li>
 * </ul>
 * 이 두 엔드포인트는 Bearer 토큰이 아니라 {@code refresh_token} 쿠키로 인증한다
 * (명세 1장 각주). 쿠키는 브라우저가 자동으로 붙이므로, {@code Origin} 없는 요청을
 * 통과시키면 제3자 페이지가 유발한 요청이 사용자 쿠키를 그대로 쓰게 된다. 그래서 이
 * 필터는 "나쁜 Origin을 거부"하는 게 아니라 <b>"신뢰하는 Origin을 요구"</b>한다 —
 * 없으면 거부다.
 *
 * <p>CORS 설정이 나중에 느슨해져도(와일드카드 추가 등) 이 두 경로는 독립적으로
 * 지켜진다는 이중 방어 효과도 있다.
 *
 * <p>응답 코드는 {@link ErrorCode#FORBIDDEN}을 쓴다. 명세 0장 4)의 16개 코드에 CSRF
 * 전용 코드가 없고, 401을 쓰면 명세 0장 6)-3)의 프론트가 재발급·재로그인 흐름으로
 * 오해하기 때문이다. 정책에 의한 거부는 403이 맞다.
 *
 * <p>일부러 {@code @Component}가 아니다. {@code Filter} 타입 빈은 Boot가 서블릿
 * 필터 체인에도 자동 등록해 전체 요청에 두 번 걸리므로, {@link SecurityConfig}가
 * 직접 생성해 security 체인에만 넣는다.
 */
@Slf4j
public class AuthOriginCheckFilter extends OncePerRequestFilter {

    /** 명세 0장 6): 쿠키로 인증하는 두 엔드포인트만 대상. 나머지는 Bearer 토큰이라 CSRF에 노출되지 않는다. */
    private static final Set<String> PROTECTED_PATHS = Set.of("/auth/refresh", "/auth/logout");

    private final ApiErrorWriter errorWriter;
    private final List<String> allowedOrigins;

    public AuthOriginCheckFilter(ApiErrorWriter errorWriter, List<String> allowedOrigins) {
        this.errorWriter = errorWriter;
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !PROTECTED_PATHS.contains(pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (origin == null || !allowedOrigins.contains(origin)) {
            // Origin 은 브라우저가 붙이는 값이라 위조할 수 없다. 값 자체는 로그에 남겨도
            // 안전하지만, 공격 시도 조사를 위해 경로와 함께 기록한다.
            log.warn("Origin 검사 실패 — path={}, origin={}", pathWithinApplication(request), origin);
            errorWriter.write(response, ErrorCode.FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** context path({@code /api/v1})를 제외한 경로. 명세의 Base URL 접두사가 매칭에 섞이지 않게 한다. */
    private static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
