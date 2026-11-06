package com.back.was.global.security;

import com.back.was.global.exception.ErrorCode;
import com.back.was.global.exception.security.ApiAuthenticationEntryPoint;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer} 헤더의 Access Token을 검증해 {@code SecurityContext}를
 * 채운다 (명세 0장 1)·6)).
 *
 * <p><b>실패해도 응답을 직접 쓰지 않고 체인을 계속 진행한다.</b> 인증을 세팅하지 않은
 * 채 통과시키면 뒤의 {@code AuthorizationFilter}가 거부하고
 * {@link ApiAuthenticationEntryPoint}가 응답을 만든다. 이 구조여야
 * <ul>
 *   <li>공개 경로(명세 인증 ✕)에 깨진 토큰이 와도 요청이 정상 처리되고,</li>
 *   <li>401 응답 형식이 EntryPoint 한 곳에서만 만들어진다.</li>
 * </ul>
 *
 * <p>실패 사유는 {@link ApiAuthenticationEntryPoint#ERROR_CODE_ATTRIBUTE}에 심어
 * 전달한다. 만료를 {@code TOKEN_EXPIRED}로 구분하는 것은 기능 요건이다 — 명세
 * 0장 6)-3)의 프론트가 그 코드를 보고 {@code /auth/refresh}를 1회 호출한 뒤 원래
 * 요청을 재시도한다. {@code UNAUTHORIZED}로 내리면 재발급 흐름이 죽고 사용자가
 * 그냥 로그아웃된다.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AuthUser authUser = jwtProvider.parse(token);
            SecurityContextHolder.getContext().setAuthentication(toAuthentication(authUser));
        } catch (ExpiredJwtException e) {
            request.setAttribute(ApiAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치·구조 오류·sub 가 숫자가 아님 등. 사유를 응답에 노출하면
            // 토큰 위조 시도에 힌트를 주므로 로그에만 남긴다.
            log.debug("Access Token 검증 실패: {}", e.getMessage());
            request.setAttribute(ApiAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
        }
    }

    private static UsernamePasswordAuthenticationToken toAuthentication(AuthUser authUser) {
        // hasRole("ADMIN") 은 ROLE_ADMIN 권한을 찾으므로 접두사를 붙여야 한다.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role()));
        return UsernamePasswordAuthenticationToken.authenticated(authUser, null, authorities);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
