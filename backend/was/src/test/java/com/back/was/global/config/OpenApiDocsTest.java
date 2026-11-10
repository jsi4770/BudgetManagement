package com.back.was.global.config;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger 문서가 실제로 만들어지는지, 그리고 <b>모든 엔드포인트가 문서에 들어가는지</b>.
 *
 * <p>이 테스트의 핵심은 두 번째다. 컨트롤러를 새로 추가하고 {@code @Operation} 을 깜빡하면
 * 문서에는 경로만 뜨고 설명이 없는 항목이 생기는데, 그건 눈으로 보지 않으면 모른다 —
 * 여기서 걸린다.
 *
 * <p>springdoc 이 Boot 4.1 에서 동작하는지에 대한 회귀 테스트이기도 하다. 버전을 올렸다가
 * 문서 생성이 깨지면 {@code /v3/api-docs} 가 500 을 내고 여기서 먼저 실패한다.
 *
 * <p>{@code /v3/api-docs} 는 인증 없이 열려 있다({@code SecurityConfig.DOCS_PATHS}) —
 * 운영 전에 닫아야 하는 항목이고(`docs/남은_작업_계획.md` 3장) 그 사실도 여기서 고정된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    /**
     * 명세의 엔드포인트 33개. {@code "METHOD /경로"} 형식이다.
     *
     * <p><b>경로에 {@code /api/v1} 이 없다.</b> springdoc 은 {@code paths} 를 context-path
     * 없이 적고 그 접두사는 {@code servers[].url} 에 넣는다 — 그래서 Swagger UI 의
     * "Try it out" 은 실제 주소로 가지만 문서상의 키는 상대 경로다.
     */
    private static final List<String> SPEC_ENDPOINTS = List.of(
            // 1장 인증
            "post /auth/signup",
            "post /auth/login",
            "post /auth/refresh",
            "post /auth/logout",
            // 2장 사용자
            "get /users/me",
            "patch /users/me",
            // 3장 예산
            "get /budgets/{yearMonth}",
            "put /budgets/{yearMonth}",
            // 4장 카테고리
            "get /categories",
            // 6장 거래
            "post /transactions",
            "get /transactions",
            "get /transactions/calendar",
            "get /transactions/{id}",
            "patch /transactions/{id}",
            "delete /transactions/{id}",
            "post /transactions/parse",
            // 7장 영수증
            "post /receipts/ocr",
            // 8장 분석
            "get /analysis/monthly",
            "get /analysis/weekly",
            "get /analysis/categories",
            // 9장 넛지
            "get /nudges/current",
            "get /nudges/{id}",
            "patch /nudges/{id}",
            // 10장 코칭
            "post /coachings",
            "get /coachings/{id}",
            "post /coachings/{id}/messages",
            "post /coachings/{id}/confirm",
            // 11장 챌린지
            "get /challenges",
            "get /challenges/{id}",
            "get /challenges/{id}/report",
            // 13장 관리자
            "get /admin/kpi"
    );

    /**
     * dev 프로필 전용이라 기본 컨텍스트의 문서에는 없어야 하는 두 개 (명세 12장).
     * 문서가 운영에서 공개될 수 있는 이상, 시연용 API 의 존재 자체가 노출되지 않는 편이 낫다.
     */
    private static final List<String> DEV_ONLY_ENDPOINTS = List.of(
            "/dev/seed",
            "/dev/challenges/{id}/complete"
    );

    @Autowired
    MockMvc mockMvc;

    private DocumentContext apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(body);
    }

    @Test
    @DisplayName("문서가 인증 없이 생성된다 — springdoc 이 Boot 4 에서 동작한다는 회귀 테스트")
    void documentIsGenerated() throws Exception {
        DocumentContext docs = apiDocs();

        assertThat(docs.<String>read("$.info.title")).isEqualTo("넛지 가계부 API");
        assertThat(docs.<Map<String, Object>>read("$.paths")).isNotEmpty();
    }

    @Test
    @DisplayName("명세 33개(dev 2개 제외 31개)가 모두 문서에 있고, 전부 summary 를 갖는다")
    void everyEndpointIsDocumented() throws Exception {
        DocumentContext docs = apiDocs();
        Map<String, Map<String, Object>> paths = docs.read("$.paths");

        assertThat(SPEC_ENDPOINTS).allSatisfy(endpoint -> {
            String[] parts = endpoint.split(" ", 2);
            String method = parts[0];
            String path = parts[1];

            assertThat(paths).as("문서에 %s 가 없어요", endpoint).containsKey(path);
            assertThat(paths.get(path)).as("%s 에 %s 메서드가 없어요", path, method)
                    .containsKey(method);

            @SuppressWarnings("unchecked")
            Map<String, Object> operation = (Map<String, Object>) paths.get(path).get(method);
            // summary 가 없으면 Swagger UI 에 경로만 뜬다 — @Operation 을 빠뜨린 신호다
            assertThat(operation.get("summary"))
                    .as("%s 에 @Operation(summary) 가 없어요", endpoint)
                    .isNotNull();
        });
    }

    @Test
    @DisplayName("Bearer 인증이 전역이고, AUTH 네 개만 예외다")
    void securityIsGlobalExceptForAuth() throws Exception {
        DocumentContext docs = apiDocs();

        assertThat(docs.<Map<String, Object>>read("$.components.securitySchemes"))
                .containsKeys("accessToken", "refreshToken");
        assertThat(docs.<List<Object>>read("$.security")).isNotEmpty();

        // 인증이 필요 없는 네 개는 빈 security 로 덮어써야 Swagger UI 가 자물쇠를 떼 준다
        for (String authPath : List.of("/auth/signup", "/auth/login",
                "/auth/refresh", "/auth/logout")) {
            assertThat(docs.<List<Object>>read("$.paths['%s'].post.security".formatted(authPath)))
                    .as("%s 는 Bearer 가 필요 없다고 표시돼야 해요", authPath)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("dev 프로필 API 는 문서에 없다 — 기본 컨텍스트에는 빈 자체가 없다")
    void devEndpointsAreAbsent() throws Exception {
        Map<String, Object> paths = apiDocs().read("$.paths");

        assertThat(paths.keySet()).doesNotContainAnyElementsOf(DEV_ONLY_ENDPOINTS);
    }

    @Test
    @DisplayName("모든 작업이 태그로 묶여 있다 — Swagger UI 의 그룹이 명세 장 번호와 맞는다")
    void everyOperationHasATag() throws Exception {
        DocumentContext docs = apiDocs();
        Map<String, Map<String, Map<String, Object>>> paths = docs.read("$.paths");

        assertThat(paths).allSatisfy((path, operations) ->
                assertThat(operations).allSatisfy((method, operation) ->
                        assertThat(operation.get("tags"))
                                .as("%s %s 에 @Tag 가 없어요", method, path)
                                .isNotNull()));
    }
}
