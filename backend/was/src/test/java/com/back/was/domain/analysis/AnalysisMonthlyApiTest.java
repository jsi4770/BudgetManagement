package com.back.was.domain.analysis;

import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.YearMonths;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
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

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ANL-01 월별 요약.
 *
 * <p>금액은 docs/시연_데이터.md 6-3 = 명세 ANL-01 응답 예시(수입 2,400,000 · 지출 666,150 ·
 * 예산 1,200,000 → 55.51%)를 그대로 쓴다. 다만 날짜는 <b>실행 시점의 KST 기준</b>으로
 * 잡는다 — 예산 자동 복사가 "이번 달"만 대상이라 고정 날짜로는 검증이 성립하지 않는다.
 *
 * <p>거래는 TX-01 대신 리포지터리로 직접 넣는다. 지난 달 거래가 필요하고, TX-01 은 미래
 * 날짜만 막으므로 과거는 API 로도 되지만, 집계 검증에 HTTP 왕복 10번을 끼울 이유가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisMonthlyApiTest {

    private static final String EMAIL = "anl-monthly@email.com";
    private static final String PASSWORD = "password123!";

    private static final YearMonth THIS_MONTH = YearMonths.current();
    private static final YearMonth LAST_MONTH = THIS_MONTH.minusMonths(1);

    /** 이번 달 안에 있고 미래가 아닌 날짜. */
    private static final LocalDate TODAY = KstTime.today();

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;
    private static final long SALARY = 7L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BudgetRepository budgetRepository;

    private String accessToken;
    private User user;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = JsonPath.read(body, "$.data.accessToken");
        user = userRepository.findByEmail(EMAIL).orElseThrow();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    /** 시연 6-3 의 한 달치 — 지출 666,150(월세 포함) · 수입 2,400,000. */
    private void givenDemoMonth(User owner, LocalDate date) {
        given(owner, SALARY, 2_400_000, date);
        given(owner, RentCategory.ID, 550_000, date);
        given(owner, DELIVERY, 101_000, date);
        given(owner, FOOD, 9_000, date);
        given(owner, CAFE, 4_500, date);
        given(owner, TRANSPORT, 1_650, date);
    }

    private void given(User owner, long categoryId, int amount, LocalDate date) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        transactionRepository.save(Transaction.of(owner, category, amount, date, null));
    }

    private void givenBudget(YearMonth yearMonth, int monthlyBudget) throws Exception {
        mockMvc.perform(authed(put("/budgets/{yearMonth}", yearMonth.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monthlyBudget": %d, "savingsGoal": 200000, "spendingFocus": "DELIVERY"}
                                """.formatted(monthlyBudget))))
                .andExpect(status().isOk());
    }

    // --- 기본 계산 ----------------------------------------------------------

    @Test
    @DisplayName("명세 ANL-01 예시: 수입 2,400,000 · 지출 666,150(월세 포함) · 잔액 1,733,850 · 남은 533,850 · 55.51%")
    void matchesTheSpecExample() throws Exception {
        givenDemoMonth(user, TODAY);
        givenBudget(THIS_MONTH, 1_200_000);

        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", THIS_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH.toString()))
                .andExpect(jsonPath("$.data.totalIncome").value(2_400_000))
                .andExpect(jsonPath("$.data.totalExpense").value(666_150))
                .andExpect(jsonPath("$.data.balance").value(1_733_850))
                .andExpect(jsonPath("$.data.budget").value(1_200_000))
                .andExpect(jsonPath("$.data.remainingBudget").value(533_850))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(55.51));
    }

    @Test
    @DisplayName("yearMonth 를 보내지 않으면 이번 달이다 (명세 기본값)")
    void defaultsToThisMonth() throws Exception {
        givenDemoMonth(user, TODAY);
        givenBudget(THIS_MONTH, 1_200_000);

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH.toString()))
                .andExpect(jsonPath("$.data.totalExpense").value(666_150));
    }

    @Test
    @DisplayName("다른 달의 거래는 섞이지 않는다 — 지난 달 지출은 지난 달 응답에만 있다")
    void scopedByMonth() throws Exception {
        givenDemoMonth(user, TODAY);
        given(user, DELIVERY, 77_000, LAST_MONTH.atEndOfMonth());

        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", THIS_MONTH.toString())))
                .andExpect(jsonPath("$.data.totalExpense").value(666_150));
        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", LAST_MONTH.toString())))
                .andExpect(jsonPath("$.data.totalExpense").value(77_000))
                .andExpect(jsonPath("$.data.totalIncome").value(0));
    }

    @Test
    @DisplayName("다른 사용자의 거래는 집계에 들어가지 않는다")
    void scopedByUser() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "anl-other@email.com", "irrelevant-hash"));
        givenDemoMonth(other, TODAY);
        given(user, DELIVERY, 10_000, TODAY);

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(jsonPath("$.data.totalExpense").value(10_000))
                .andExpect(jsonPath("$.data.totalIncome").value(0));
    }

    @Test
    @DisplayName("거래가 한 건도 없는 달도 200 + 0원이다 (빈 달에 404 는 없다)")
    void emptyMonthIsZero() throws Exception {
        givenBudget(THIS_MONTH, 1_200_000);

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIncome").value(0))
                .andExpect(jsonPath("$.data.totalExpense").value(0))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.remainingBudget").value(1_200_000))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(0.00));
    }

    // --- 예산 (서버_내부_처리 5장) -------------------------------------------

    @Test
    @DisplayName("이번 달 예산 행이 없으면 가장 최근 예산을 복사해서 계산한다 (행도 생긴다)")
    void copiesTheLatestBudgetForThisMonth() throws Exception {
        givenDemoMonth(user, TODAY);
        givenBudget(LAST_MONTH, 1_200_000);
        assertThat(budgetRepository.findByUserIdAndYearMonth(user.getId(), THIS_MONTH.toString()))
                .isEmpty();

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget").value(1_200_000))
                .andExpect(jsonPath("$.data.remainingBudget").value(533_850))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(55.51));

        assertThat(budgetRepository.findByUserIdAndYearMonth(user.getId(), THIS_MONTH.toString()))
                .isPresent();
    }

    @Test
    @DisplayName("예산을 한 번도 설정하지 않으면 budget·remainingBudget 은 null, 사용률은 0.00 — 404 가 아니다")
    void noBudgetAtAllIsStillTwoHundred() throws Exception {
        givenDemoMonth(user, TODAY);

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpense").value(666_150))
                .andExpect(jsonPath("$.data.budget").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.remainingBudget").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(0.00));
    }

    @Test
    @DisplayName("과거 월은 예산을 자동 생성하지 않는다 — remainingBudget null, 사용률 0.00 (거래 합계는 그대로)")
    void pastMonthWithoutBudget() throws Exception {
        given(user, DELIVERY, 77_000, LAST_MONTH.atEndOfMonth());
        givenBudget(THIS_MONTH, 1_200_000);

        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", LAST_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpense").value(77_000))
                .andExpect(jsonPath("$.data.budget").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.remainingBudget").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(0.00));

        assertThat(budgetRepository.findByUserIdAndYearMonth(user.getId(), LAST_MONTH.toString()))
                .isEmpty();
    }

    @Test
    @DisplayName("예산을 0원으로 설정해도 사용률은 0.00 이다 (0 나눗셈 방지) — budget 은 0 으로 남는다")
    void zeroBudgetDoesNotDivideByZero() throws Exception {
        givenDemoMonth(user, TODAY);
        givenBudget(THIS_MONTH, 0);

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget").value(0))
                .andExpect(jsonPath("$.data.remainingBudget").value(-666_150))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(0.00));
    }

    @Test
    @DisplayName("예산을 초과하면 remainingBudget 은 음수, 사용률은 100 을 넘는다")
    void overspending() throws Exception {
        givenDemoMonth(user, TODAY);
        givenBudget(THIS_MONTH, 500_000);

        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(jsonPath("$.data.remainingBudget").value(-166_150))
                .andExpect(jsonPath("$.data.budgetUsageRate").value(133.23));
    }

    // --- 검증·인증 ----------------------------------------------------------

    @Test
    @DisplayName("yearMonth 형식이 YYYY-MM 이 아니면 400 VALIDATION_ERROR")
    void rejectsMalformedYearMonth() throws Exception {
        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", "2026-13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", "2026-11-04")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/analysis/monthly"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
