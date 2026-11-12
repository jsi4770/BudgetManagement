package com.back.was.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서의 머리말과 인증 방식. 엔드포인트별 설명은 각 컨트롤러의
 * {@code @Tag}·{@code @Operation} 에 있다.
 *
 * <p>요청 스키마는 대부분 자동으로 만들어진다 — springdoc 이 Bean Validation 애너테이션
 * ({@code @NotNull}·{@code @Size}·{@code @Min} …)을 읽어 {@code required}·길이·범위를
 * 채우기 때문이다. 그래서 DTO 에 {@code @Schema} 를 덧붙이는 것은 <b>애너테이션으로
 * 드러나지 않는 규칙</b>(형식 문자열, enum 값의 의미 등)에만 한다.
 *
 * <p><b>서버 URL 을 적지 않는다.</b> springdoc 이 요청에서 호스트와
 * {@code server.servlet.context-path=/api/v1} 를 읽어 {@code servers[].url} 을 만들어
 * 준다(로컬이면 {@code http://localhost:8080/api/v1}). 그래서 Swagger UI 의 "Try it out"
 * 이 실제 주소로 가고, {@code paths} 의 키는 접두사 없는 상대 경로가 된다.
 *
 * <p>여기에 {@code servers} 를 손으로 적으면 배포 환경마다 틀리거나 접두사가 두 번 붙는다 —
 * 리버스 프록시 뒤에 둘 때는 {@code server.forward-headers-strategy} 로 푸는 문제다.
 *
 * <p><b>이 문서는 지금 인증 없이 열려 있다</b>({@code SecurityConfig.DOCS_PATHS}).
 * 운영 노출 전에 닫거나 dev 프로필로 제한해야 한다 — `docs/남은_작업_계획.md` 3장.
 */
@Configuration
public class OpenApiConfig {

    /** {@code Authorization: Bearer {accessToken}} (명세 0장 1)). */
    private static final String BEARER_SCHEME = "accessToken";

    /** AUTH-03·04 가 쓰는 쿠키 (명세 0장 6)). */
    private static final String REFRESH_COOKIE_SCHEME = "refreshToken";

    private static final String DESCRIPTION = """
            넛지형 AI 재무 코칭 가계부 백엔드. 상세 규칙은 `docs/API_명세서.md` 를 따릅니다.

            ### 공통 응답 (0장 3))
            성공은 `{ "success": true, "data": ... }`, 실패는 `{ "success": false, "error": { "code", "message", "details" } }` 입니다.
            두 키가 동시에 나오는 일은 없습니다. 목록은 `data` 안에 페이지 정보가 들어갑니다(`PageResponse`).

            ### 인증 (0장 1))
            회원가입·로그인·재발급·로그아웃 네 개를 뺀 모든 요청에 `Authorization: Bearer {accessToken}` 이 필요합니다.
            아래 **Authorize** 에 액세스 토큰을 넣으면 이 문서에서 바로 호출할 수 있습니다.

            재발급(AUTH-03)과 로그아웃(AUTH-04)은 Bearer 가 아니라 `refresh_token` **쿠키**로 인증하고,
            CSRF 방어 때문에 **신뢰된 `Origin` 헤더가 필수**입니다 — 없으면 컨트롤러에 닿기 전에 403 입니다(0장 6)).
            그래서 이 두 개는 Swagger UI 에서 바로 시험하기 어렵습니다.

            ### 에러 코드 (0장 4))
            16개로 고정돼 있습니다. 엔드포인트별로 어떤 코드가 나올 수 있는지는 각 API 의 Responses 를 보세요.
            **다른 사용자의 리소스는 403 이 아니라 404** 입니다(0장 5)) — ID 를 훑어서 남의 데이터 존재 여부를 알 수 없게 하려는 규칙입니다.
            403 은 관리자 API 권한 부족에만 씁니다(지금은 해당 엔드포인트가 없습니다).

            ### 날짜·금액·비율 (0장 2))
            날짜는 `YYYY-MM-DD`, 시각은 KST 오프셋을 포함한 `2026-11-04T19:30:00+09:00`,
            금액은 원 단위 정수, 비율은 소수점 2자리입니다.

            ### ⚠️ 아직 구현되지 않은 15개
            명세 33개 중 **18개만** 이 문서에 있습니다. AI 코칭과 소비 분석 모듈이 개발 중이라
            아래는 **매핑 자체가 없어 404** 입니다 — 프론트에서 호출하면 안 됩니다.

            - **ANL-03** 카테고리별 평소 대비 비교 (`GET /analysis/categories`)
            - **NDG-01~03** 넛지 (`/nudges/**`)
            - **COACH-01~04** AI 코칭 (`/coachings/**`)
            - **CHL-01~03** 챌린지·성장 리포트 (`/challenges/**`)
            - **ADMIN-01** 관리자 KPI (`GET /admin/kpi`)
            - **TX-07** 자연어 분석 (`POST /transactions/parse`) — Gemini 어댑터 대기
            - **RCPT-01** 영수증 OCR (`POST /receipts/ocr`) — OCR 서버 대기
            - **DEV-02** 챌린지 강제 종료 (dev 전용이라 원래 이 문서에 없습니다)

            거래를 등록·수정·삭제해도 **챌린지 실적과 넛지가 재계산되지 않습니다** — 거래 후처리도
            위 기능들이 완성된 뒤에 붙습니다. 진행 상황은 `docs/AI_코칭_소비분석_인수인계.md` 를 보세요.
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("넛지 가계부 API")
                        .version("v1")
                        .description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("AUTH-01·02 응답의 `accessToken`. 유효 시간 6시간."))
                        .addSecuritySchemes(REFRESH_COOKIE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("refresh_token")
                                .description("""
                                        AUTH-01·02 가 `Set-Cookie` 로 내려주는 httpOnly 쿠키 (30일).
                                        AUTH-03·04 만 이 방식으로 인증합니다. 브라우저가 자동으로 보냅니다.""")))
                // 기본값을 Bearer 로 둔다 — 33개 중 29개가 이 방식이라 엔드포인트마다
                // 적는 대신 전역으로 걸고, 예외인 네 개(AUTH-*)에만 @SecurityRequirements 로
                // 비워 준다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
