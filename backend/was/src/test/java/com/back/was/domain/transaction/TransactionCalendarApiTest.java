package com.back.was.domain.transaction;

import com.back.was.global.support.KstTime;
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

import java.time.LocalDate;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-03 달력용 일별 합계. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionCalendarApiTest {

    /** ERD 4장 시드: 2 = 배달(EXPENSE), 5 = 월세(EXPENSE), 7 = 급여(INCOME). */
    private static final long DELIVERY = 2L;
    private static final long RENT = 5L;
    private static final long SALARY = 7L;

    /**
     * 기준 월은 <b>지난 달</b>이다. 말일 경계를 확인해야 하는데 이번 달 말일은 아직
     * 오지 않았을 수 있고, TX-01 이 미래 날짜를 막아 거래를 만들 수 없다.
     */
    private static final YearMonth TARGET = YearMonth.from(KstTime.today()).minusMonths(1);
    private static final LocalDate FIRST_DAY = TARGET.atDay(1);
    private static final LocalDate LAST_DAY = TARGET.atEndOfMonth();

    @Autowired
    MockMvc mockMvc;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        accessToken = signUp("tx-calendar@email.com");
    }

    private String signUp(String email) throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "password123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private void given(long categoryId, int amount, LocalDate date) throws Exception {
        mockMvc.perform(authed(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %d, "transactionDate": "%s"}
                                """.formatted(categoryId, amount, date))))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder calendar(String yearMonth) {
        return authed(get("/transactions/calendar").param("yearMonth", yearMonth));
    }

    @Test
    @DisplayName("TX-03: 하루에 수입·지출이 섞여도 한 줄로, 각각 합산해서 준다 (시연 데이터 1일)")
    void sumsIncomeAndExpensePerDay() throws Exception {
        given(SALARY, 2400000, FIRST_DAY);
        given(RENT, 550000, FIRST_DAY);

        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.yearMonth").value(TARGET.toString()))
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value(FIRST_DAY.toString()))
                .andExpect(jsonPath("$.data.days[0].totalIncome").value(2400000))
                .andExpect(jsonPath("$.data.days[0].totalExpense").value(550000));
    }

    @Test
    @DisplayName("TX-03: 같은 날 같은 종류의 거래 여러 건은 합산된다")
    void sumsMultipleTransactionsOfSameKind() throws Exception {
        given(DELIVERY, 24000, FIRST_DAY);
        given(DELIVERY, 8650, FIRST_DAY);

        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].totalExpense").value(32650))
                .andExpect(jsonPath("$.data.days[0].totalIncome").value(0));
    }

    @Test
    @DisplayName("TX-03: 거래가 있는 날짜만 나오고, 날짜 오름차순이다")
    void returnsOnlyDaysWithTransactionsInAscendingOrder() throws Exception {
        given(DELIVERY, 30000, FIRST_DAY.plusDays(10));
        given(DELIVERY, 10000, FIRST_DAY);
        given(DELIVERY, 20000, FIRST_DAY.plusDays(5));

        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].date").value(FIRST_DAY.toString()))
                .andExpect(jsonPath("$.data.days[1].date").value(FIRST_DAY.plusDays(5).toString()))
                .andExpect(jsonPath("$.data.days[2].date").value(FIRST_DAY.plusDays(10).toString()))
                // 사이의 거래 없는 날짜는 0 으로 채우지 않는다.
                .andExpect(jsonPath("$.data.days[?(@.date == '%s')]"
                        .formatted(FIRST_DAY.plusDays(1))).doesNotExist());
    }

    @Test
    @DisplayName("TX-03: 1일·말일은 포함하고 인접한 달의 거래는 제외한다")
    void respectsMonthBoundaries() throws Exception {
        given(DELIVERY, 10000, FIRST_DAY.minusDays(1));   // 전달 말일
        given(DELIVERY, 20000, FIRST_DAY);                // 대상 월 1일
        given(DELIVERY, 30000, LAST_DAY);                 // 대상 월 말일
        given(DELIVERY, 40000, LAST_DAY.plusDays(1));     // 다음 달 1일

        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].date").value(FIRST_DAY.toString()))
                .andExpect(jsonPath("$.data.days[0].totalExpense").value(20000))
                .andExpect(jsonPath("$.data.days[1].date").value(LAST_DAY.toString()))
                .andExpect(jsonPath("$.data.days[1].totalExpense").value(30000));
    }

    @Test
    @DisplayName("TX-03: 거래가 없는 달은 404 가 아니라 빈 days 다")
    void emptyMonthReturnsEmptyDays() throws Exception {
        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(TARGET.toString()))
                .andExpect(jsonPath("$.data.days").isArray())
                .andExpect(jsonPath("$.data.days.length()").value(0));
    }

    @Test
    @DisplayName("TX-03: 다른 사용자의 거래는 합계에 섞이지 않는다")
    void calendarIsScopedToTokenOwner() throws Exception {
        given(DELIVERY, 24000, FIRST_DAY);

        accessToken = signUp("tx-calendar-other@email.com");

        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(0));

        given(DELIVERY, 1000, FIRST_DAY);

        mockMvc.perform(calendar(TARGET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].totalExpense").value(1000));
    }

    @Test
    @DisplayName("TX-03: yearMonth 는 필수 — 빠지면 400 VALIDATION_ERROR (field=yearMonth, REQUIRED)")
    void yearMonthIsRequired() throws Exception {
        mockMvc.perform(authed(get("/transactions/calendar")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'yearMonth')].reason")
                        .value(org.hamcrest.Matchers.hasItem("REQUIRED")));
    }

    @Test
    @DisplayName("TX-03: yearMonth 형식이 YYYY-MM 이 아니면 400")
    void malformedYearMonthIsRejected() throws Exception {
        for (String bad : new String[]{"2026-13", "2026-1", "202611", "2026-11-04", "abcd-ef"}) {
            mockMvc.perform(calendar(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    @DisplayName("TX-03: 토큰 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/transactions/calendar").param("yearMonth", TARGET.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
