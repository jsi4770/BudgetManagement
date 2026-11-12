package com.back.was.integration;

import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>지금 구현되어 있는 엔드포인트 전부를 한 사용자의 시나리오로 한 번에 통과시키는 통합
 * 테스트.</b>
 *
 * <p>도메인별 테스트들이 각 API 의 규칙을 깊게 파는 것과 목적이 다르다. 여기서 보는 것은
 * <b>연결</b>이다 — 가입으로 받은 토큰이 다음 요청의 인증에 쓰이고, DEV-01 이 깐 거래가
 * 분석 화면의 숫자가 되는 한 줄기. 중간에 어디가 끊기면 그 단계에서 실패한다.
 *
 * <p><b>명세 33개 중 13개가 빠져 있다</b> — ANL-03, NDG-01~03, CHL-01~03, COACH-01~04,
 * DEV-02, ADMIN-01, 그리고 거래 후처리 2·3·5번. 이상치 탐지 · 달성률 계산 · 성장 리포트 ·
 * 넛지 도메인 · 코칭 · 챌린지가 별도 담당자의 작업으로 넘어갔기 때문이다
 * ({@code docs/인수인계_넛지_이상치_리포트.md}). 영향을 받은 단계마다 <b>되살릴 단정값을
 * javadoc 에 적어 뒀다</b> — 구현이 들어오면 그걸 되돌리고 {@code @Order} 를 다시 매기면
 * 된다.
 *
 * <p><b>실제 요청·응답만 본다.</b> 서비스나 리포지토리를 직접 부르지 않고 전부 MockMvc
 * 요청이며(필터 체인 · 컨트롤러 · 예외 핸들러 전부 통과), 다음 단계에 필요한 값은
 * 응답 JSON 에서만 꺼낸다. 리포지토리는 주입하지 않고, 정리에만 SQL 을 쓴다.
 *
 * <p><b>왜 {@code @Transactional} 이 아닌가</b>: 후처리 5번(넛지 AI 문구)은
 * {@code AFTER_COMMIT} 리스너이고 종료 판정은 {@code REQUIRES_NEW} 라서, 테스트가
 * 트랜잭션을 쥐고 있으면 둘 다 보이지 않는다. 그래서 실제로 커밋하고
 * {@link #cleanUp()} 에서 <b>내가 만든 사용자의 행만</b> 지운다 —
 * {@code users} 전체를 비우면 같은 dev 컨텍스트를 쓰는 다른 테스트의 관리자 계정이
 * 사라진다. {@code @Async} 는 {@link SynchronousExecution} 이 동기로 바꿔 끼운다
 * ({@code NudgeTextApiTest} 와 같은 이유).
 *
 * <p><b>왜 dev 프로필인가</b>: DEV-01 은 그 프로필에서만 등록된다(서버_내부_처리 12장).
 * 원래는 ADMIN-01 을 통과할 관리자 계정도 {@code AdminSeeder} 가 거기서만 만들기 때문이기도
 * 했는데, ADMIN-01 단계는 인수인계와 함께 빠졌다.
 *
 * <p><b>단정값의 출처</b>: DEV-01 이 깔아 주는 시연_데이터 3장의 데이터는 요일 보정
 * 규칙(3-5) 때문에 <b>주간 숫자가 시연 요일과 무관하게 같다</b> — 그래서 주간 총지출
 * 116,150원을 날짜와 상관없이 단정할 수 있다(시연_데이터 6-1). 반대로 <b>월 누적은
 * 경과일에 따라 달라지므로</b> 월별 요약에서는 고정값(1일의 급여·월세) 과
 * "예산 − 총지출 = 잔액" 같은 불변식만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullApiJourneyIntegrationTest {

    /** 후처리 5번을 호출 스레드에서 실행시켜 "언젠가 채워진다"를 기다리지 않게 한다. */
    @TestConfiguration
    static class SynchronousExecution {

        @Bean
        Executor applicationTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    private static final String EMAIL = "journey@email.com";
    private static final String STRANGER_EMAIL = "journey-stranger@email.com";
    private static final String PASSWORD = "password123!";

    /** CORS 허용 오리진. {@code /auth/refresh}·{@code /auth/logout} 은 이게 없으면 403. */
    private static final String ORIGIN = "http://localhost:5173";

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate THIS_MONDAY = Weeks.mondayOf(TODAY);
    private static final LocalDate FIRST_OF_MONTH = TODAY.withDayOfMonth(1);
    private static final String THIS_MONTH = YearMonth.from(TODAY).toString();

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long RENT = 5L;

    /**
     * 이번 주 지출 합계 (시연 3-4 DELIVERY_SPIKE) — 요일 보정 덕에 요일과 무관하게 같다.
     *
     * <p>이 안에 들어 있는 배달 101,000원 / 평균 60,000원 / +68.33% 는 ANL-03 과 넛지가
     * 돌아올 때 쓸 값이다(시연_데이터 6-1). 지금은 단정하는 단계가 없어 상수를 두지 않는다.
     */
    private static final int WEEK_EXPENSE = 116_150;

    /** 1일에 들어가는 급여·월세 (시연 3-3). 월 어느 날에 돌려도 이번 달에 포함된다. */
    private static final int MONTHLY_SALARY = 2_400_000;
    private static final int MONTHLY_RENT = 550_000;

    /** DEV-01 이 까는 직전 달 예산 — BUDGET-01 의 자동 복사 원본이 된다. */
    private static final int SEEDED_BUDGET = 1_200_000;
    private static final int SEEDED_SAVINGS = 200_000;

    private static final int MY_BUDGET = 1_500_000;
    private static final int MY_SAVINGS = 300_000;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;
    private String refreshToken;
    private String strangerToken;

    private long transactionId;

    /**
     * 커밋하는 테스트라 직접 치운다. 자식 테이블부터 지우고, 범위는 <b>이 테스트가 만든
     * 사용자</b>로 한정한다 — {@code users} 전체를 비우면 같은 dev 컨텍스트의 관리자 계정이
     * 사라져 {@code AdminSeederTest} 가 깨진다.
     */
    @AfterAll
    void cleanUp() {
        List<Long> userIds = jdbcTemplate.queryForList(
                "select id from users where email in (?, ?)", Long.class, EMAIL, STRANGER_EMAIL);

        for (Long userId : userIds) {
            // 코칭·챌린지·성장 리포트·넛지 테이블은 인수인계와 함께 사라졌다. 돌아오면
            // **자식부터** 지우는 순서를 되살릴 것 (FK 가 모두 RESTRICT):
            //   growth_reports → challenges → coaching_messages → coachings
            //   → transactions → nudges → budgets
            // CHALLENGE·GROWTH_REPORT 에는 USER_ID 가 없어(ERD 9·10장) 코칭을 경유하는
            // 서브쿼리로 범위를 잡아야 한다.
            jdbcTemplate.update("delete from transactions where user_id = ?", userId);
            jdbcTemplate.update("delete from budgets where user_id = ?", userId);
            jdbcTemplate.update("delete from refresh_token where user_id = ?", userId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
    }

    // --- 요청 헬퍼 ----------------------------------------------------------

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return as(request, accessToken);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,
                                                    String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request,
                                                      String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static long longAt(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static int intAt(String body, String path) {
        return ((Number) JsonPath.read(body, path)).intValue();
    }

    /** 응답 바디가 아니라 {@code Set-Cookie} 로만 나가는 Refresh Token 을 꺼낸다 (0장 6). */
    private static String refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refresh_token=");
        return setCookie.substring("refresh_token=".length()).split(";")[0];
    }

    // --- 1. 가입 · 내 정보 ---------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("AUTH-01 가입 — 201 + Access Token 바디 · Refresh Token 쿠키")
    void signUp() throws Exception {
        MvcResult result = mockMvc.perform(json(post("/auth/signup"), """
                        {"username": "홍길동", "email": "%s", "password": "%s"}
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(21_600))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn();

        accessToken = JsonPath.read(bodyOf(result), "$.data.accessToken");
        refreshToken = refreshCookieOf(result);

        assertThat(bodyOf(result)).doesNotContain(refreshToken);
    }

    @Test
    @Order(2)
    @DisplayName("USER-01 내 정보 — 가입에서 받은 토큰이 그대로 인증에 쓰인다")
    void getMe() throws Exception {
        mockMvc.perform(authed(get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("홍길동"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"))
                // 0장 2): _AT 응답에는 오프셋이 붙는다
                .andExpect(jsonPath("$.data.createdAt").value(Matchers.containsString("+09:00")));
    }

    @Test
    @Order(3)
    @DisplayName("USER-02 닉네임 수정 — 보낸 필드만 바뀌고 email·role 은 끼워 넣을 수 없다")
    void updateMe() throws Exception {
        mockMvc.perform(authed(json(patch("/users/me"), """
                        {"username": "길동이", "email": "hacker@email.com", "role": "ADMIN"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("길동이"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @Order(4)
    @DisplayName("CAT-01 카테고리 — 시드 9건이 ID 순 배열로 (ERD 4장)")
    void getCategories() throws Exception {
        mockMvc.perform(authed(get("/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].id").value(FOOD))
                .andExpect(jsonPath("$.data[0].name").value("식비"))
                .andExpect(jsonPath("$.data[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data[4].id").value(RENT))
                .andExpect(jsonPath("$.data[4].name").value("월세"))
                .andExpect(jsonPath("$.data[6].type").value("INCOME"))
                // 고정 9행이라 PageResponse 가 아니다 (명세 CAT-01)
                .andExpect(jsonPath("$.data.page").doesNotExist());

        mockMvc.perform(authed(get("/categories").param("type", "INCOME")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    // --- 2. 시연 데이터 · 예산 ----------------------------------------------

    @Test
    @Order(5)
    @DisplayName("DEV-01 시연 데이터 — 3개월치 거래와 과거 챌린지 4건을 깐다")
    void seedDemoData() throws Exception {
        mockMvc.perform(authed(json(post("/dev/seed"), """
                        {"scenario": "DELIVERY_SPIKE"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenario").value("DELIVERY_SPIKE"))
                .andExpect(jsonPath("$.data.transactionCount")
                        .value(Matchers.greaterThan(100)))
                // 넛지 판정이 비어 있어 카드가 생기지 않는다 — 판정이 돌아오면
                // DELIVERY_SPIKE 의 이 값은 true 여야 한다 (시연_데이터 6-1).
                .andExpect(jsonPath("$.data.nudgeCreated").value(false));
    }

    @Test
    @Order(6)
    @DisplayName("BUDGET-01 이번 달 예산 — 행이 없으면 가장 최근 달을 복사해 만든다 (ERD 3장)")
    void getBudgetCopiesTheMostRecentMonth() throws Exception {
        // DEV-01 은 직전 3개월만 깐다 — 이번 달 조회가 그 복사 규칙을 밟는다
        mockMvc.perform(authed(get("/budgets/{yearMonth}", THIS_MONTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH))
                .andExpect(jsonPath("$.data.monthlyBudget").value(SEEDED_BUDGET))
                .andExpect(jsonPath("$.data.savingsGoal").value(SEEDED_SAVINGS))
                .andExpect(jsonPath("$.data.spendingFocus").value("DELIVERY"));
    }

    @Test
    @Order(7)
    @DisplayName("BUDGET-02 예산 저장 — PUT 이라 보내지 않은 필드는 비워진다")
    void saveBudget() throws Exception {
        mockMvc.perform(authed(json(put("/budgets/{yearMonth}", THIS_MONTH), """
                        {"monthlyBudget": %d, "savingsGoal": %d, "spendingFocus": "DELIVERY"}
                        """.formatted(MY_BUDGET, MY_SAVINGS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyBudget").value(MY_BUDGET))
                .andExpect(jsonPath("$.data.savingsGoal").value(MY_SAVINGS));

        mockMvc.perform(authed(json(put("/budgets/{yearMonth}", THIS_MONTH), """
                        {"monthlyBudget": %d}
                        """.formatted(MY_BUDGET))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savingsGoal").value(0))
                .andExpect(jsonPath("$.data.spendingFocus").value(Matchers.nullValue()));

        // 다음 단계(ANL-01)가 쓸 값으로 되돌려 둔다
        mockMvc.perform(authed(json(put("/budgets/{yearMonth}", THIS_MONTH), """
                        {"monthlyBudget": %d, "savingsGoal": %d, "spendingFocus": "DELIVERY"}
                        """.formatted(MY_BUDGET, MY_SAVINGS))))
                .andExpect(status().isOk());
    }

    // --- 3. 거래 (TX-01 ~ TX-07) -------------------------------------------

    @Test
    @Order(8)
    @DisplayName("TX-01 거래 등록 — 201, 카테고리 객체가 함께 나오고 미래 날짜는 400 FUTURE_DATE")
    void createTransaction() throws Exception {
        MvcResult result = mockMvc.perform(authed(json(post("/transactions"), """
                        {"categoryId": %d, "amount": 12000, "transactionDate": "%s", "memo": "점심"}
                        """.formatted(FOOD, TODAY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.amount").value(12_000))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.memo").value("점심"))
                .andExpect(jsonPath("$.data.category.id").value(FOOD))
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"))
                .andReturn();

        transactionId = longAt(bodyOf(result), "$.data.id");

        mockMvc.perform(authed(json(post("/transactions"), """
                        {"categoryId": %d, "amount": 1000, "transactionDate": "%s"}
                        """.formatted(FOOD, TODAY.plusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'transactionDate')].reason")
                        .value(Matchers.hasItem("FUTURE_DATE")));
    }

    @Test
    @Order(9)
    @DisplayName("TX-02 거래 목록 — 기본 범위는 이번 달 1일~오늘, 방금 넣은 건이 맨 앞")
    void listTransactions() throws Exception {
        mockMvc.perform(authed(get("/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(Matchers.greaterThan(1)))
                // 정렬은 transactionDate desc, createdAt desc — 가장 늦게 만든 내 거래가 처음
                .andExpect(jsonPath("$.data.items[0].id").value(transactionId))
                .andExpect(jsonPath("$.data.items[0].category.name").value("식비"))
                // 목록 항목에는 createdAt 이 없다 (명세 TX-02)
                .andExpect(jsonPath("$.data.items[0].createdAt").doesNotExist());

        mockMvc.perform(authed(get("/transactions")
                        .param("categoryId", String.valueOf(DELIVERY))
                        .param("from", THIS_MONDAY.toString())
                        .param("to", TODAY.toString())
                        .param("size", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].category.id")
                        .value(Matchers.everyItem(Matchers.is((int) DELIVERY))));

        // size 상한은 깎지 않고 거절한다 (프런트 페이징 계산이 어긋나므로)
        mockMvc.perform(authed(get("/transactions").param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(10)
    @DisplayName("TX-03 달력 — 날짜 오름차순, 1일에 급여 240만 · 월세 55만이 한 줄로 합쳐진다")
    void getCalendar() throws Exception {
        // 1일의 지출은 월세 '이상'이다 — 매주 반복 거래가 실제 요일에 들어가므로
        // 이번 달 1일이 지난주의 어떤 요일과 겹치면 그 거래도 같은 날에 올라온다
        mockMvc.perform(authed(get("/transactions/calendar").param("yearMonth", THIS_MONTH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH))
                .andExpect(jsonPath("$.data.days[0].date").value(FIRST_OF_MONTH.toString()))
                .andExpect(jsonPath("$.data.days[0].totalIncome").value(MONTHLY_SALARY))
                .andExpect(jsonPath("$.data.days[0].totalExpense")
                        .value(Matchers.greaterThanOrEqualTo(MONTHLY_RENT)));

        // 필수 파라미터 누락은 400 VALIDATION_ERROR + details[].reason = REQUIRED
        mockMvc.perform(authed(get("/transactions/calendar")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field == 'yearMonth')].reason")
                        .value(Matchers.hasItem("REQUIRED")));
    }

    @Test
    @Order(11)
    @DisplayName("TX-04 거래 상세 — /calendar 는 {id} 로 먹히지 않는다")
    void getTransaction() throws Exception {
        mockMvc.perform(authed(get("/transactions/{id}", transactionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(transactionId))
                .andExpect(jsonPath("$.data.amount").value(12_000))
                .andExpect(jsonPath("$.data.createdAt")
                        .value(Matchers.containsString("+09:00")));
    }

    @Test
    @Order(12)
    @DisplayName("TX-05 거래 수정 — 보낸 필드만, 카테고리를 바꾸면 방향도 바뀔 수 있다")
    void updateTransaction() throws Exception {
        mockMvc.perform(authed(json(patch("/transactions/{id}", transactionId), """
                        {"amount": 18000, "categoryId": %d}
                        """.formatted(CAFE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(18_000))
                .andExpect(jsonPath("$.data.category.id").value(CAFE))
                // 보내지 않은 memo·transactionDate 는 그대로
                .andExpect(jsonPath("$.data.memo").value("점심"))
                .andExpect(jsonPath("$.data.transactionDate").value(TODAY.toString()));

        // 메모 지우기는 빈 문자열 (null 은 "그대로 두기")
        mockMvc.perform(authed(json(patch("/transactions/{id}", transactionId), """
                        {"memo": ""}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value(Matchers.nullValue()));
    }

    @Test
    @Order(13)
    @DisplayName("TX-06 거래 삭제 — 204, 두 번 지우면 404 (멱등하지 않다)")
    void deleteTransaction() throws Exception {
        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/transactions/{id}", transactionId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    @DisplayName("TX-07 자연어 파싱 — Gemini 어댑터가 없어 503 AI_UNAVAILABLE (명세 TX-07)")
    void parseTransactionText() throws Exception {
        mockMvc.perform(authed(json(post("/transactions/parse"), """
                        {"text": "어제 스타벅스에서 커피 5500원"}
                        """)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(authed(json(post("/transactions/parse"), "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(15)
    @DisplayName("RCPT-01 영수증 OCR — OCR 서버가 없어 502 OCR_FAILED (유일한 multipart API)")
    void analyzeReceipt() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-jpeg-bytes".getBytes());

        // multipart 빌더는 MockHttpServletRequestBuilder 계열이 아니라 authed() 를 못 쓴다
        mockMvc.perform(multipart("/receipts/ocr").file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("OCR_FAILED"));

        // image 파트가 없으면 400 (advice 에 MissingServletRequestPartException 매핑이 있다)
        mockMvc.perform(multipart("/receipts/ocr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- 4. 분석 (ANL-01 ~ ANL-03) -----------------------------------------

    @Test
    @Order(16)
    @DisplayName("ANL-01 월별 요약 — 1일의 급여가 수입, 예산 − 총지출 = 잔액")
    void getMonthlySummary() throws Exception {
        MvcResult result = mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(THIS_MONTH))
                .andExpect(jsonPath("$.data.totalIncome").value(MONTHLY_SALARY))
                .andExpect(jsonPath("$.data.totalExpense")
                        .value(Matchers.greaterThanOrEqualTo(MONTHLY_RENT)))
                .andExpect(jsonPath("$.data.budget").value(MY_BUDGET))
                .andReturn();

        // 월 누적은 경과일에 따라 달라지므로 값이 아니라 불변식을 본다
        String body = bodyOf(result);
        int income = intAt(body, "$.data.totalIncome");
        int expense = intAt(body, "$.data.totalExpense");
        assertThat(intAt(body, "$.data.balance")).isEqualTo(income - expense);
        assertThat(intAt(body, "$.data.remainingBudget")).isEqualTo(MY_BUDGET - expense);
        assertThat(JsonPath.<Object>read(body, "$.data.budgetUsageRate")).isNotNull();

        // 미전송은 이번 달, 빈 문자열은 400 — 잘못된 값을 "이번 달"로 넘기지 않는다
        mockMvc.perform(authed(get("/analysis/monthly").param("yearMonth", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(17)
    @DisplayName("ANL-02 주간 추이 — 기본 4주, 라벨은 월~일이고 이번 주 합계는 월~오늘")
    void getWeeklyTrend() throws Exception {
        mockMvc.perform(authed(get("/analysis/weekly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks.length()").value(4))
                .andExpect(jsonPath("$.data.weeks[0].weekStart")
                        .value(THIS_MONDAY.minusWeeks(3).toString()))
                .andExpect(jsonPath("$.data.weeks[3].weekStart").value(THIS_MONDAY.toString()))
                // 라벨은 오늘이 아니라 이번 주 일요일 (명세 ANL-02 예시)
                .andExpect(jsonPath("$.data.weeks[3].weekEnd")
                        .value(THIS_MONDAY.plusDays(6).toString()))
                // 합계는 월~오늘, 월세 제외 → 요일과 무관하게 116,150 (시연 3-4)
                .andExpect(jsonPath("$.data.weeks[3].totalExpense").value(WEEK_EXPENSE));

        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks.length()").value(8));

        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "53")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- 6. 권한 · 소유권 (0장 1) · 5)) ------------------------------------

    /**
     * ADMIN-01(403 이 나는 유일한 경로)은 코칭·챌린지와 함께 인수인계되어 이 여정에서
     * 빠졌다. 401/403 의 본문 모양 자체는 {@code SecurityConfigTest} 가 자체
     * {@code TestController} 로 계속 고정하고 있다. KPI 가 돌아오면 여기에
     * "토큰 없음 → 401 · USER 토큰 → 403 · ADMIN 토큰 → 200 + target 70.00" 단계를
     * 되살릴 것 ({@code adminSeeder.run(null)} 를 먼저 불러야 한다 — 같은 dev 컨텍스트의
     * 다른 테스트가 관리자 행을 지웠을 수 있고, 그 호출은 멱등이다).
     *
     * <p>소유권 확인은 <b>거래</b>로 한다. 원래는 코칭·챌린지 id 로도 확인했는데 둘 다
     * 없어졌다 — 돌아오면 {@code /coachings/{id}}, {@code /challenges/{id}},
     * {@code /challenges/{id}/report}, {@code /nudges/{id}} 를 아래 목록에 더할 것.
     */
    @Test
    @Order(18)
    @DisplayName("남의 리소스는 403 이 아니라 404 — 없는 id 응답과 구별되지 않는다 (0장 5))")
    void otherUsersResourcesAreNotFound() throws Exception {
        MvcResult signup = mockMvc.perform(json(post("/auth/signup"), """
                        {"username": "남의사람", "email": "%s", "password": "%s"}
                        """.formatted(STRANGER_EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        strangerToken = JsonPath.read(bodyOf(signup), "$.data.accessToken");

        // TX-06 이 앞에서 transactionId 를 지웠으므로 확인용으로 하나 새로 만든다.
        MvcResult created = mockMvc.perform(authed(json(post("/transactions"), """
                        {"categoryId": %d, "amount": 3000, "transactionDate": "%s"}
                        """.formatted(FOOD, TODAY))))
                .andExpect(status().isCreated())
                .andReturn();
        long myTransactionId = longAt(bodyOf(created), "$.data.id");

        assertSameNotFound("/transactions/{id}", myTransactionId, "99999999");
    }

    /** 남의 것과 없는 것의 응답이 <b>바이트 단위로 같은지</b> 본다 — 존재 여부가 새면 안 된다. */
    private void assertSameNotFound(String path, long mine, String missing) throws Exception {
        String othersResponse = bodyOf(mockMvc.perform(as(get(path, mine), strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andReturn());

        String missingResponse = bodyOf(mockMvc.perform(as(get(path, missing), strangerToken))
                .andExpect(status().isNotFound())
                .andReturn());

        assertThat(othersResponse).isEqualTo(missingResponse);
    }

    // --- 10. 세션 (AUTH-02 ~ AUTH-04) --------------------------------------

    @Test
    @Order(19)
    @DisplayName("AUTH-02 로그인 — 예산이 있으니 hasBudget: true")
    void login() throws Exception {
        MvcResult result = mockMvc.perform(json(post("/auth/login"), """
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("길동이"))
                .andExpect(jsonPath("$.data.hasBudget").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        accessToken = JsonPath.read(bodyOf(result), "$.data.accessToken");
        refreshToken = refreshCookieOf(result);

        mockMvc.perform(json(post("/auth/login"), """
                        {"email": "%s", "password": "wrongPassword1!"}
                        """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @Order(20)
    @DisplayName("AUTH-03 토큰 재발급 — Origin 없으면 403, 회전된 옛 토큰은 401")
    void refresh() throws Exception {
        // CSRF 대체 장치: Origin 이 없는 요청은 컨트롤러에 닿지 못한다 (0장 6))
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        String usedToken = refreshToken;
        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie("refresh_token", usedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(21_600))
                // 명세 AUTH-03 에는 user·hasBudget 이 없다
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andReturn();

        accessToken = JsonPath.read(bodyOf(result), "$.data.accessToken");
        refreshToken = refreshCookieOf(result);
        assertThat(refreshToken).isNotEqualTo(usedToken);

        // 새로 받은 Access Token 이 실제로 통한다
        mockMvc.perform(authed(get("/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        // 회전된 옛 토큰은 그 자리에서 죽는다 (DB 행이 진실 — ERD 2장)
        mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie("refresh_token", usedToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @Order(21)
    @DisplayName("AUTH-04 로그아웃 — 204 + 만료 쿠키, 다시 불러도 204 (멱등)")
    void logout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        Matchers.containsString("Max-Age=0")));

        mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        // 쿠키가 없어도, 이미 무효인 토큰이어도 204 — 실패를 알리면 그 토큰이 유효했다는 뜻이 된다
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNoContent());
    }
}
