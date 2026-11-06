package com.back.was.domain.transaction;

import com.back.was.global.support.KstTime;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TX-02 거래 목록. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionListApiTest {

    /** ERD 4장 시드: 1 = 식비(EXPENSE), 2 = 배달(EXPENSE), 7 = 급여(INCOME). */
    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long SALARY = 7L;

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate FIRST_OF_MONTH = TODAY.withDayOfMonth(1);
    private static final LocalDate LAST_MONTH = FIRST_OF_MONTH.minusDays(1);

    @Autowired
    MockMvc mockMvc;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        accessToken = signUp("tx-list@email.com");
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

    private long given(long categoryId, int amount, LocalDate date, String memo) throws Exception {
        String body = mockMvc.perform(authed(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %d, "transactionDate": "%s", "memo": "%s"}
                                """.formatted(categoryId, amount, date, memo))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    @Test
    @DisplayName("TX-02: data 안에 items·page·size·totalCount·hasNext 가 들어간다 (명세 0장 3))")
    void returnsPageEnvelope() throws Exception {
        given(DELIVERY, 24000, TODAY, "치킨");

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].id").isNumber())
                .andExpect(jsonPath("$.data.items[0].category.id").value(2))
                .andExpect(jsonPath("$.data.items[0].category.name").value("배달"))
                .andExpect(jsonPath("$.data.items[0].category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.items[0].amount").value(24000))
                .andExpect(jsonPath("$.data.items[0].transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.items[0].memo").value("치킨"));
    }

    @Test
    @DisplayName("TX-02: 목록 원소에는 createdAt 이 없다 (TX-01·TX-04 와 다른 점)")
    void itemHasNoCreatedAt() throws Exception {
        given(DELIVERY, 24000, TODAY, "치킨");

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].createdAt").doesNotExist());
    }

    @Test
    @DisplayName("TX-02: 기본 기간은 이번 달 1일 ~ 오늘 — 지난 달 거래는 빠지고 1일 거래는 들어온다")
    void defaultRangeIsThisMonth() throws Exception {
        given(DELIVERY, 10000, LAST_MONTH, "지난달");
        given(DELIVERY, 20000, FIRST_OF_MONTH, "1일");
        given(DELIVERY, 30000, TODAY, "오늘");

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].memo",
                        Matchers.not(Matchers.hasItem("지난달"))))
                .andExpect(jsonPath("$.data.items[*].memo", Matchers.hasItem("1일")))
                .andExpect(jsonPath("$.data.items[*].memo", Matchers.hasItem("오늘")));
    }

    @Test
    @DisplayName("TX-02: from·to 를 주면 그 기간만 — 달력 날짜 선택(from=to) 포함")
    void explicitRangeIsRespected() throws Exception {
        given(DELIVERY, 10000, LAST_MONTH, "지난달");
        given(DELIVERY, 20000, TODAY, "오늘");

        mockMvc.perform(authed(get("/transactions")
                        .param("from", LAST_MONTH.toString())
                        .param("to", LAST_MONTH.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].memo").value("지난달"));
    }

    @Test
    @DisplayName("TX-02: 정렬은 transactionDate 내림차순 → 같은 날짜는 createdAt 내림차순")
    void sortsByDateThenCreatedAtDescending() throws Exception {
        given(DELIVERY, 10000, TODAY.minusDays(2), "이틀전");
        // 같은 날짜의 순서는 createdAt 으로 갈리므로 등록 간격을 둔다.
        given(DELIVERY, 20000, TODAY, "먼저");
        Thread.sleep(5);
        given(DELIVERY, 30000, TODAY, "나중");

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].memo").value("나중"))
                .andExpect(jsonPath("$.data.items[1].memo").value("먼저"))
                .andExpect(jsonPath("$.data.items[2].memo").value("이틀전"));
    }

    @Test
    @DisplayName("TX-02: type 은 카테고리 TYPE 으로 걸러진다 (거래에는 수입/지출 컬럼이 없다)")
    void filtersByCategoryType() throws Exception {
        given(DELIVERY, 24000, TODAY, "치킨");
        given(FOOD, 9000, TODAY, "김밥");
        given(SALARY, 2500000, TODAY, "월급");

        mockMvc.perform(authed(get("/transactions").param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[*].category.type",
                        Matchers.everyItem(Matchers.is("EXPENSE"))));

        mockMvc.perform(authed(get("/transactions").param("type", "INCOME")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].memo").value("월급"));
    }

    @Test
    @DisplayName("TX-02: categoryId 로 걸러지고, type 과 함께 주면 둘 다 만족해야 한다")
    void filtersByCategoryIdAndCombinesWithType() throws Exception {
        given(DELIVERY, 24000, TODAY, "치킨");
        given(FOOD, 9000, TODAY, "김밥");
        given(SALARY, 2500000, TODAY, "월급");

        mockMvc.perform(authed(get("/transactions").param("categoryId", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].memo").value("치킨"));

        mockMvc.perform(authed(get("/transactions")
                        .param("type", "EXPENSE")
                        .param("categoryId", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].memo").value("김밥"));

        // 교차 조건: 배달은 EXPENSE 라서 INCOME 과 함께 주면 결과가 없다.
        mockMvc.perform(authed(get("/transactions")
                        .param("type", "INCOME")
                        .param("categoryId", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("TX-02: page·size 로 나뉘고 hasNext 가 마지막 페이지에서 false 가 된다")
    void paginates() throws Exception {
        given(DELIVERY, 10000, TODAY.minusDays(2), "셋");
        given(DELIVERY, 20000, TODAY.minusDays(1), "둘");
        given(DELIVERY, 30000, TODAY, "하나");

        mockMvc.perform(authed(get("/transactions").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].memo").value("하나"));

        mockMvc.perform(authed(get("/transactions")
                        .param("size", "2")
                        .param("page", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].memo").value("셋"));
    }

    @Test
    @DisplayName("TX-02: 홈 '최근 거래'는 size=3")
    void homeRecentTransactionsUseSizeThree() throws Exception {
        for (int i = 0; i < 5; i++) {
            given(DELIVERY, 1000 * (i + 1), TODAY.minusDays(i), "거래" + i);
        }

        mockMvc.perform(authed(get("/transactions").param("size", "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("TX-02: size 는 최대 100 — 101 이면 400 VALIDATION_ERROR")
    void sizeOverLimitIsRejected() throws Exception {
        mockMvc.perform(authed(get("/transactions").param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'size')]").exists());

        mockMvc.perform(authed(get("/transactions").param("size", "100")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TX-02: size 0·음수 page 는 400")
    void invalidPagingIsRejected() throws Exception {
        mockMvc.perform(authed(get("/transactions").param("size", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(authed(get("/transactions").param("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TX-02: 명세에 없는 type·잘못된 날짜 형식은 400")
    void malformedParametersAreRejected() throws Exception {
        mockMvc.perform(authed(get("/transactions").param("type", "SAVINGS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(authed(get("/transactions").param("from", "2026/11/04")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TX-02: from 이 to 보다 뒤면 에러가 아니라 빈 목록이다")
    void reversedRangeYieldsEmptyList() throws Exception {
        given(DELIVERY, 24000, TODAY, "치킨");

        mockMvc.perform(authed(get("/transactions")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.minusDays(7).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("TX-02: 다른 사용자의 거래는 목록에 섞이지 않는다")
    void listIsScopedToTokenOwner() throws Exception {
        given(DELIVERY, 24000, TODAY, "내거래");

        accessToken = signUp("tx-list-other@email.com");

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        given(FOOD, 9000, TODAY, "남의거래");

        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].memo").value("남의거래"));
    }

    @Test
    @DisplayName("TX-02: 토큰 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
