package com.back.was.domain.budget;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BUDGET-01 월 예산·목표 조회 · BUDGET-02 저장. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetApiTest {

    private static final String EMAIL = "budget@email.com";
    private static final String PASSWORD = "password123!";

    /** 자동 복사는 "이번 달"만 대상이라 테스트도 실행 시점의 KST 연월을 기준으로 잡는다. */
    private static final YearMonth THIS_MONTH = YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
    private static final YearMonth LAST_MONTH = THIS_MONTH.minusMonths(1);
    private static final YearMonth THREE_MONTHS_AGO = THIS_MONTH.minusMonths(3);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BudgetRepository budgetRepository;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        accessToken = signUp(EMAIL);
    }

    private String signUp(String email) throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private void putBudget(YearMonth yearMonth, String json) throws Exception {
        mockMvc.perform(authed(put("/budgets/{yearMonth}", yearMonth.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)))
                .andExpect(status().isOk());
    }

    // --- BUDGET-02 저장 ---------------------------------------------------

    @Test
    @DisplayName("BUDGET-02: 행이 없으면 생성하고 BUDGET-01 과 같은 모양으로 응답한다")
    void createsWhenAbsent() throws Exception {
        mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH.toString()))
                .andExpect(jsonPath("$.data.monthlyBudget").value(1200000))
                .andExpect(jsonPath("$.data.savingsGoal").value(200000))
                .andExpect(jsonPath("$.data.spendingFocus").value("DELIVERY"));
    }

    @Test
    @DisplayName("BUDGET-02: 같은 연월로 다시 호출하면 행이 늘지 않고 덮어써진다")
    void overwritesWhenPresent() throws Exception {
        putBudget(THIS_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": 900000, "savingsGoal": 100000, "spendingFocus": "CAFE"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyBudget").value(900000))
                .andExpect(jsonPath("$.data.spendingFocus").value("CAFE"));

        assertThat(budgetRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("BUDGET-02: savingsGoal 미전송은 기본값 0, spendingFocus 미전송은 null (키는 남는다)")
    void optionalFieldsFallBackToDefaults() throws Exception {
        String body = mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": 1200000}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savingsGoal").value(0))
                .andExpect(jsonPath("$.data.spendingFocus").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();

        // 키 자체는 남아야 한다 — NON_NULL 은 ApiResponse 의 data/error 에만 걸려 있다.
        assertThat(body).contains("\"spendingFocus\":null");
    }

    @Test
    @DisplayName("BUDGET-02: monthlyBudget 누락은 400 VALIDATION_ERROR (field=monthlyBudget, REQUIRED)")
    void monthlyBudgetIsRequired() throws Exception {
        mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"savingsGoal": 200000}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'monthlyBudget')].reason")
                        .value(org.hamcrest.Matchers.hasItem("REQUIRED")));
    }

    @Test
    @DisplayName("BUDGET-02: 음수 금액은 400 (명세 0 이상)")
    void negativeAmountsAreRejected() throws Exception {
        mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": -1}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": 1200000, "savingsGoal": -1}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("BUDGET-02: 명세에 없는 spendingFocus 값은 400")
    void unknownSpendingFocusIsRejected() throws Exception {
        mockMvc.perform(authed(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": 1200000, "spendingFocus": "SHOPPING"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("BUDGET-02: 이번 달 저장은 지난 달 행을 건드리지 않는다 (마이페이지 수정 규칙)")
    void savingThisMonthLeavesPastMonthsAlone() throws Exception {
        putBudget(LAST_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);
        putBudget(THIS_MONTH, """
                {"monthlyBudget": 700000, "savingsGoal": 0, "spendingFocus": "CAFE"}
                """);

        mockMvc.perform(authed(get("/budgets/{yearMonth}", LAST_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyBudget").value(1200000))
                .andExpect(jsonPath("$.data.spendingFocus").value("DELIVERY"));
    }

    // --- BUDGET-01 조회 ---------------------------------------------------

    @Test
    @DisplayName("BUDGET-01: 예산을 한 번도 설정하지 않았으면 404 NOT_FOUND → 초기 설정 화면")
    void notFoundWhenNeverConfigured() throws Exception {
        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(budgetRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("BUDGET-01: 저장한 달은 그대로 돌려준다")
    void returnsSavedMonth() throws Exception {
        putBudget(THIS_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH.toString()))
                .andExpect(jsonPath("$.data.monthlyBudget").value(1200000))
                .andExpect(jsonPath("$.data.savingsGoal").value(200000))
                .andExpect(jsonPath("$.data.spendingFocus").value("DELIVERY"));
    }

    @Test
    @DisplayName("BUDGET-01: 이번 달 행이 없으면 가장 최근 월을 복사해 생성한다 (시연 데이터 2장)")
    void copiesMostRecentMonthIntoThisMonth() throws Exception {
        putBudget(LAST_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH.toString()))
                .andExpect(jsonPath("$.data.monthlyBudget").value(1200000))
                .andExpect(jsonPath("$.data.savingsGoal").value(200000))
                .andExpect(jsonPath("$.data.spendingFocus").value("DELIVERY"));

        assertThat(budgetRepository.findAll()).hasSize(2);
        assertThat(budgetRepository.findAll())
                .extracting(Budget::getYearMonth)
                .containsExactlyInAnyOrder(LAST_MONTH.toString(), THIS_MONTH.toString());
    }

    @Test
    @DisplayName("BUDGET-01: 지난 달이 비어 있어도 '가장 최근' 월(3개월 전)에서 복사한다")
    void copiesFromMostRecentNotStrictlyLastMonth() throws Exception {
        putBudget(THREE_MONTHS_AGO, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH.toString()))
                .andExpect(jsonPath("$.data.monthlyBudget").value(1200000));
    }

    @Test
    @DisplayName("BUDGET-01: 과거 월은 자동 생성하지 않는다 — 없으면 404, 행도 안 생긴다")
    void pastMonthIsNeverAutoCreated() throws Exception {
        putBudget(THIS_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        mockMvc.perform(authed(get("/budgets/{yearMonth}", LAST_MONTH.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(budgetRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("BUDGET-01: 미래 월의 예산을 이번 달로 끌어오지 않는다")
    void doesNotCopyFromFutureMonth() throws Exception {
        putBudget(THIS_MONTH.plusMonths(1), """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("연월 형식이 YYYY-MM 이 아니면 400 VALIDATION_ERROR")
    void malformedYearMonthIsRejected() throws Exception {
        for (String bad : new String[]{"2026-13", "2026-1", "202611", "2026-11-04", "abcd-ef"}) {
            mockMvc.perform(authed(get("/budgets/{yearMonth}", bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    @DisplayName("다른 사용자의 예산은 보이지 않는다 — 403 이 아니라 404 (명세 0장 5))")
    void otherUsersBudgetIsNotFound() throws Exception {
        putBudget(THIS_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        accessToken = signUp("other@email.com");

        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/budgets/{yearMonth}", THIS_MONTH.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/budgets/{yearMonth}", THIS_MONTH.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": 1200000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("AUTH-02 hasBudget: BUDGET-02 로 예산을 저장한 뒤 로그인하면 true 가 된다")
    void hasBudgetBecomesTrueAfterSaving() throws Exception {
        String before = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(before, "$.data.hasBudget")).isFalse();

        putBudget(THIS_MONTH, """
                {"monthlyBudget": 1200000, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                """);

        String after = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(after, "$.data.hasBudget")).isTrue();
    }
}
