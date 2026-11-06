package com.back.was.global.security;

import com.back.was.global.exception.security.ApiAccessDeniedHandler;
import com.back.was.global.exception.security.ApiAuthenticationEntryPoint;
import com.back.was.global.exception.security.ApiErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API 명세서 0장 1)·6)의 인증·CORS 규칙을 필터 체인으로 옮긴다.
 *
 * <p>이 설정의 핵심 목적은 {@link ApiAuthenticationEntryPoint}·
 * {@link ApiAccessDeniedHandler} 배선이다. 둘을 등록하지 않으면 401·403만 Spring
 * 기본 형식으로 나가고 나머지 에러는 명세 형식으로 나가는 불일치가 생긴다
 * (필터 단계 예외는 {@code @RestControllerAdvice}에 도달하지 않는다).
 *
 * <p>경로는 {@code server.servlet.context-path=/api/v1} 아래에서 해석되므로
 * 여기 matcher에는 {@code /api/v1} 접두사를 쓰지 않는다 — Security matcher는
 * context path를 제외한 경로를 본다.
 *
 * <p>커스텀 필터 둘을 {@code AuthorizationFilter} 앞에 끼운다:
 * {@link AuthOriginCheckFilter}(명세 0장 6) CSRF 방어)와
 * {@link JwtAuthenticationFilter}(Bearer 토큰 검증). 둘 다 빈이 아니다 — 아래
 * 생성 메서드의 주석 참고.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /** 명세 0장 1): 인증 ✕ 인 4개 엔드포인트. AUTH-03·04는 Bearer 대신 refresh_token 쿠키로 인증하므로 체인에서는 열어두고 서비스가 쿠키를 검증한다. */
    private static final String[] PUBLIC_POST_PATHS = {
            "/auth/signup",
            "/auth/login",
            "/auth/refresh",
            "/auth/logout"
    };

    /**
     * springdoc UI·스펙 경로.
     *
     * <p>운영 노출 시에는 닫거나 dev 프로필로 제한해야 한다 — 전체 API 구조가
     * 인증 없이 읽히기 때문이다. 지금은 개발 편의를 위해 열어둔다.
     */
    private static final String[] DOCS_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;
    private final ApiErrorWriter errorWriter;
    private final JwtProvider jwtProvider;

    /** 명세 0장 1): `*` 불가, 프론트 도메인만. 환경별로 다르므로 설정값으로 뺀다. */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // JWT 기반 무상태 API라 세션 CSRF 토큰이 성립하지 않는다. 명세 0장 6)이
                // 요구하는 CSRF 방어는 AuthOriginCheckFilter 가 Origin 헤더 검사로 대신한다.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본 HTTP Basic·폼 로그인을 끄지 않으면 그쪽 EntryPoint가 우선해
                // 브라우저 인증 팝업(WWW-Authenticate)이 뜨고 JSON이 나가지 않는다.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .requestMatchers(DOCS_PATHS).permitAll()
                        // 명세 0장 5) 예외: 기능 자체에 권한이 필요한 경우만 403.
                        // 남의 리소스 접근은 여기서 막지 않고 서비스 계층이 404로 처리한다.
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // CorsFilter 뒤, 컨트롤러 앞. 대상이 permitAll 경로라 인가 단계보다 먼저
                // 걸러야 의미가 있다. 매칭은 필터가 스스로 하므로 위치는 "충분히 앞"이면 된다.
                .addFilterBefore(authOriginCheckFilter(), AuthorizationFilter.class)
                // 인가 판단 전에 SecurityContext 가 채워져 있어야 한다.
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), AuthorizationFilter.class);

        return http.build();
    }

    /**
     * 빈으로 노출하지 않는다 — {@code Filter} 타입 빈은 Boot가 서블릿 필터 체인에도
     * 자동 등록해 security 체인 밖에서 모든 요청에 한 번 더 걸린다.
     * {@link JwtAuthenticationFilter} 도 같은 이유로 직접 생성한다.
     *
     * <p>허용 Origin 목록을 CORS 설정과 같은 필드에서 넘겨 단일 출처를 유지한다.
     */
    private AuthOriginCheckFilter authOriginCheckFilter() {
        return new AuthOriginCheckFilter(errorWriter, allowedOrigins);
    }

    /**
     * ERD 1장: 비밀번호는 bcrypt. 해시 결과가 60자라 USER.PASSWORD_HASH 는 255자로 충분하다.
     *
     * <p>Refresh Token 해시에는 쓰지 말 것 — bcrypt 는 같은 값도 매번 다른 해시를
     * 만들어 조회가 불가능하다 (ERD 2장). 그쪽은 SHA-256 을 쓴다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 명세 0장 1)·6): Allow-Origin 에 `*` 불가 + Allow-Credentials: true.
        // 쿠키(refresh_token) 송수신 때문에 credentials 가 필수다.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
