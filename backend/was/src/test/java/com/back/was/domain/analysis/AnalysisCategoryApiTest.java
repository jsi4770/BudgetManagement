package com.back.was.domain.analysis;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
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

/**
 * ANL-03 카테고리별 평소 대비 비교.
 *
 * <p>기대값은 docs/시연_데이터.md 6-1(주간)·6-3(월간)이다. 날짜는 실행 시점의 KST 주·월
 * 경계로 잡고, 각 구간의 금액은 그 구간의 <b>시작일</b>(주는 월요일, 달은 1일)에 몰아 넣는다
 * — 시작일은 항상 오늘 이하라 미래 날짜가 되지 않는다.
 *
 * <p>주간 표본(직전 4주)과 월간 표본(직전 3개월)은 날짜가 겹칠 수 있어서 fixture 를 따로
 * 둔다. 예를 들어 4주 전 월요일은 지난 달 1~8일 구간에 들어갈 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisCategoryApiTest {

    private static final String EMAIL = "anl-category@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate THIS_MONDAY = Weeks.mondayOf(TODAY);
    private static final LocalDate FIRST_OF_MONTH = TODAY.withDayOfMonth(1);

    /** 시연 1장: 가입일 D − 150일 — 주간 4주·월간 3개월 비교가 모두 성립하는 값. */
    private static final LocalDate LONG_TIME_MEMBER = TODAY.minusDays(150);

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;
    private static final long ETC = 6L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    private String accessToken;
    private Long userId;
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
        userId = user.getId();
        backdateSignUp(LONG_TIME_MEMBER);
    }

    /**
     * 가입일을 과거로 옮긴다. {@code users.created_at} 은 {@code @CreationTimestamp} +
     * {@code updatable = false} 라 JPA 로는 바꿀 수 없어서 네이티브 UPDATE 를 쓴다 — DEV-01
     * 도 같은 일을 해야 한다(시연_데이터 7장 "USER.CREATED_AT도 D − 150일로").
     */
    private void backdateSignUp(LocalDate signUpDate) {
        entityManager.createNativeQuery("update users set created_at = :at where id = :id")
                .setParameter("at", signUpDate.atStartOfDay())
                .setParameter("id", userId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        user = userRepository.findById(userId).orElseThrow();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private void given(long categoryId, int amount, LocalDate date) {
        given(user, categoryId, amount, date);
    }

    private void given(User owner, long categoryId, int amount, LocalDate date) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        transactionRepository.save(Transaction.of(owner, category, amount, date, null));
    }

    /** 시연 6-1: 이번 주 + 직전 4주 표본. 기타는 양쪽 모두 0원이다. */
    private void givenWeeklyFixture() {
        given(DELIVERY, 101_000, THIS_MONDAY);
        given(FOOD, 9_000, THIS_MONDAY);
        given(CAFE, 4_500, THIS_MONDAY);
        given(TRANSPORT, 1_650, THIS_MONDAY);

        int[] deliverySamples = {55_000, 62_000, 58_000, 65_000};
        for (int weeksAgo = 1; weeksAgo <= 4; weeksAgo++) {
            LocalDate monday = THIS_MONDAY.minusWeeks(weeksAgo);
            given(DELIVERY, deliverySamples[weeksAgo - 1], monday);
            given(FOOD, 17_500, monday);
            given(CAFE, 4_500, monday);
            given(TRANSPORT, 9_300, monday);
        }
    }

    /** 시연 6-3: 이번 달(월세 포함) + 직전 3개월 표본. */
    private void givenMonthlyFixture() {
        given(DELIVERY, 101_000, FIRST_OF_MONTH);
        given(FOOD, 9_000, FIRST_OF_MONTH);
        given(CAFE, 4_500, FIRST_OF_MONTH);
        given(TRANSPORT, 1_650, FIRST_OF_MONTH);
        given(RentCategory.ID, 550_000, FIRST_OF_MONTH);

        LocalDate oneMonthAgo = FIRST_OF_MONTH.minusMonths(1);
        given(FOOD, 41_500, oneMonthAgo);
        given(CAFE, 10_500, oneMonthAgo);
        given(TRANSPORT, 6_200, oneMonthAgo);

        LocalDate twoMonthsAgo = FIRST_OF_MONTH.minusMonths(2);
        given(DELIVERY, 33_000, twoMonthsAgo);
        given(FOOD, 18_000, twoMonthsAgo);
        given(CAFE, 9_000, twoMonthsAgo);
        given(TRANSPORT, 12_400, twoMonthsAgo);

        LocalDate threeMonthsAgo = FIRST_OF_MONTH.minusMonths(3);
        given(DELIVERY, 27_000, threeMonthsAgo);
        given(FOOD, 41_000, threeMonthsAgo);
        given(CAFE, 10_500, threeMonthsAgo);
        given(TRANSPORT, 6_200, threeMonthsAgo);
        given(ETC, 15_000, threeMonthsAgo);
    }

    // --- WEEK ---------------------------------------------------------------

    @Test
    @DisplayName("시연 6-1: period 미전송이면 WEEK, 월요일 ~ 오늘 — 배달 평균 60,000 · +68.33% · 이상 ⚠️")
    void weekIsTheDefault() throws Exception {
        givenWeeklyFixture();

        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.period").value("WEEK"))
                .andExpect(jsonPath("$.data.startDate").value(THIS_MONDAY.toString()))
                .andExpect(jsonPath("$.data.endDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.dataSufficient").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(116_150))
                // 지출 카테고리 전체를 ID 순으로 (월세 제외 → 5개)
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].category.id").value(FOOD))
                .andExpect(jsonPath("$.data.items[0].category.name").value("식비"))
                .andExpect(jsonPath("$.data.items[0].amount").value(9_000))
                .andExpect(jsonPath("$.data.items[0].averageAmount").value(17_500))
                .andExpect(jsonPath("$.data.items[0].growthRate").value(-48.57))
                .andExpect(jsonPath("$.data.items[0].isAnomaly").value(false))
                .andExpect(jsonPath("$.data.items[1].category.name").value("배달"))
                .andExpect(jsonPath("$.data.items[1].amount").value(101_000))
                .andExpect(jsonPath("$.data.items[1].averageAmount").value(60_000))
                .andExpect(jsonPath("$.data.items[1].growthRate").value(68.33))
                .andExpect(jsonPath("$.data.items[1].isAnomaly").value(true))
                .andExpect(jsonPath("$.data.items[2].category.name").value("카페"))
                .andExpect(jsonPath("$.data.items[2].averageAmount").value(4_500))
                .andExpect(jsonPath("$.data.items[2].growthRate").value(0.00))
                .andExpect(jsonPath("$.data.items[2].isAnomaly").value(false))
                .andExpect(jsonPath("$.data.items[3].category.name").value("교통"))
                .andExpect(jsonPath("$.data.items[3].growthRate").value(-82.26));
    }

    @Test
    @DisplayName("category 객체에는 type 이 없다 — CAT-01 과 키 구성이 다르다 (명세 ANL-03)")
    void categoryRefHasNoType() throws Exception {
        givenWeeklyFixture();

        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].category.type").doesNotExist());
    }

    @Test
    @DisplayName("지출이 없는 카테고리도 0원으로 들어간다 — 표본도 0원이면 평균 0, 증가율은 null")
    void zeroSpendingCategoryIsIncluded() throws Exception {
        givenWeeklyFixture();

        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(jsonPath("$.data.items[4].category.name").value("기타"))
                .andExpect(jsonPath("$.data.items[4].amount").value(0))
                .andExpect(jsonPath("$.data.items[4].averageAmount").value(0))
                .andExpect(jsonPath("$.data.items[4].growthRate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[4].isAnomaly").value(false));
    }

    @Test
    @DisplayName("기록이 없는 주는 0원으로 표본에 들어간다 (ERD 6장) — 2주만 기록해도 4로 나눈다")
    void emptySampleWeeksCountAsZero() throws Exception {
        given(DELIVERY, 101_000, THIS_MONDAY);
        given(DELIVERY, 55_000, THIS_MONDAY.minusWeeks(1));
        given(DELIVERY, 65_000, THIS_MONDAY.minusWeeks(2));

        // (0 + 0 + 55,000 + 65,000) ÷ 4 = 30,000 → (101,000 − 30,000) ÷ 30,000 = +236.67%
        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(jsonPath("$.data.items[1].category.name").value("배달"))
                .andExpect(jsonPath("$.data.items[1].averageAmount").value(30_000))
                .andExpect(jsonPath("$.data.items[1].growthRate").value(236.67));
    }

    @Test
    @DisplayName("WEEK 은 월세를 항목에서도 totalAmount 에서도 뺀다 (ERD 4장)")
    void weekExcludesRentEntirely() throws Exception {
        givenWeeklyFixture();
        given(RentCategory.ID, 550_000, THIS_MONDAY);

        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[*].category.name",
                        Matchers.not(Matchers.hasItem("월세"))))
                .andExpect(jsonPath("$.data.totalAmount").value(116_150));
    }

    // --- MONTH --------------------------------------------------------------

    @Test
    @DisplayName("시연 6-3: MONTH 은 1일 ~ 오늘, 월세 포함 총지출 666,150 — 배달 평균 20,000 · +405.00%")
    void monthComparesAgainstThreeMonths() throws Exception {
        givenMonthlyFixture();

        mockMvc.perform(authed(get("/analysis/categories").param("period", "MONTH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("MONTH"))
                .andExpect(jsonPath("$.data.startDate").value(FIRST_OF_MONTH.toString()))
                .andExpect(jsonPath("$.data.endDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.dataSufficient").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(666_150))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].category.name").value("식비"))
                .andExpect(jsonPath("$.data.items[0].averageAmount").value(33_500))
                .andExpect(jsonPath("$.data.items[0].growthRate").value(-73.13))
                .andExpect(jsonPath("$.data.items[1].category.name").value("배달"))
                .andExpect(jsonPath("$.data.items[1].averageAmount").value(20_000))
                .andExpect(jsonPath("$.data.items[1].growthRate").value(405.00))
                .andExpect(jsonPath("$.data.items[2].averageAmount").value(10_000))
                .andExpect(jsonPath("$.data.items[2].growthRate").value(-55.00))
                // 교통: (6,200 + 12,400 + 6,200) ÷ 3 = 8,266.67 → 8,267 로 반올림
                .andExpect(jsonPath("$.data.items[3].category.name").value("교통"))
                .andExpect(jsonPath("$.data.items[3].averageAmount").value(8_267))
                .andExpect(jsonPath("$.data.items[3].growthRate").value(-80.04))
                .andExpect(jsonPath("$.data.items[5].category.name").value("기타"))
                .andExpect(jsonPath("$.data.items[5].averageAmount").value(5_000))
                .andExpect(jsonPath("$.data.items[5].growthRate").value(-100.00));
    }

    @Test
    @DisplayName("MONTH 는 이상 표시를 하지 않는다 — 배달이 +405% 라도 isAnomaly 는 false")
    void monthNeverFlagsAnomaly() throws Exception {
        givenMonthlyFixture();

        mockMvc.perform(authed(get("/analysis/categories").param("period", "MONTH")))
                .andExpect(jsonPath("$.data.items[1].growthRate").value(405.00))
                .andExpect(jsonPath("$.data.items[*].isAnomaly",
                        Matchers.everyItem(Matchers.is(false))));
    }

    @Test
    @DisplayName("MONTH 월세: 항목·총지출에는 있고 평균·증가율만 null (납부일이 달마다 다름)")
    void monthKeepsRentButNotItsComparison() throws Exception {
        givenMonthlyFixture();

        mockMvc.perform(authed(get("/analysis/categories").param("period", "MONTH")))
                .andExpect(jsonPath("$.data.items[4].category.id").value(RentCategory.ID))
                .andExpect(jsonPath("$.data.items[4].category.name").value("월세"))
                .andExpect(jsonPath("$.data.items[4].amount").value(550_000))
                .andExpect(jsonPath("$.data.items[4].averageAmount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[4].growthRate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[4].isAnomaly").value(false));
    }

    @Test
    @DisplayName("baseDate 를 주면 그 날짜 기준으로 구간이 잡힌다 — 지난 달 말일이면 지난 달 1일부터")
    void baseDateMovesThePeriod() throws Exception {
        LocalDate endOfLastMonth = FIRST_OF_MONTH.minusDays(1);

        mockMvc.perform(authed(get("/analysis/categories")
                        .param("period", "MONTH")
                        .param("baseDate", endOfLastMonth.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value(endOfLastMonth.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.data.endDate").value(endOfLastMonth.toString()));
    }

    // --- dataSufficient (명세 ANL-03) ---------------------------------------

    @Test
    @DisplayName("가입 4주 미만이면 dataSufficient false — 평균·증가율은 null, 금액은 그대로")
    void insufficientDataHidesTheComparison() throws Exception {
        givenWeeklyFixture();
        backdateSignUp(TODAY.minusDays(7));

        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataSufficient").value(false))
                .andExpect(jsonPath("$.data.totalAmount").value(116_150))
                .andExpect(jsonPath("$.data.items[1].amount").value(101_000))
                .andExpect(jsonPath("$.data.items[*].averageAmount",
                        Matchers.everyItem(Matchers.nullValue())))
                .andExpect(jsonPath("$.data.items[*].growthRate",
                        Matchers.everyItem(Matchers.nullValue())))
                .andExpect(jsonPath("$.data.items[*].isAnomaly",
                        Matchers.everyItem(Matchers.is(false))));
    }

    @Test
    @DisplayName("경계: 가입일이 표본 첫 주 월요일이면 충분, 하루라도 뒤면 부족")
    void dataSufficiencyBoundaryIsInclusive() throws Exception {
        LocalDate firstSampleStart = AnalysisPeriod.WEEK.oldestSampleStart(TODAY);

        backdateSignUp(firstSampleStart);
        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(jsonPath("$.data.dataSufficient").value(true));

        backdateSignUp(firstSampleStart.plusDays(1));
        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(jsonPath("$.data.dataSufficient").value(false));
    }

    @Test
    @DisplayName("MONTH 는 직전 3개월 첫날이 기준이다 — 두 달 전 가입이면 부족")
    void monthNeedsThreeMonthsOfHistory() throws Exception {
        backdateSignUp(AnalysisPeriod.MONTH.oldestSampleStart(TODAY).plusDays(1));

        mockMvc.perform(authed(get("/analysis/categories").param("period", "MONTH")))
                .andExpect(jsonPath("$.data.dataSufficient").value(false));
    }

    // --- 격리·검증·인증 -----------------------------------------------------

    @Test
    @DisplayName("다른 사용자의 거래는 금액에도 표본에도 섞이지 않는다")
    void scopedByUser() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "anl-category-other@email.com", "irrelevant-hash"));
        given(other, DELIVERY, 500_000, THIS_MONDAY);
        given(other, DELIVERY, 500_000, THIS_MONDAY.minusWeeks(1));
        given(DELIVERY, 101_000, THIS_MONDAY);

        mockMvc.perform(authed(get("/analysis/categories")))
                .andExpect(jsonPath("$.data.totalAmount").value(101_000))
                .andExpect(jsonPath("$.data.items[1].amount").value(101_000))
                .andExpect(jsonPath("$.data.items[1].averageAmount").value(0));
    }

    @Test
    @DisplayName("period 가 WEEK·MONTH 가 아니면 400 VALIDATION_ERROR")
    void rejectsUnknownPeriod() throws Exception {
        mockMvc.perform(authed(get("/analysis/categories").param("period", "YEAR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("period"));
    }

    @Test
    @DisplayName("baseDate 형식이 날짜가 아니면 400 VALIDATION_ERROR")
    void rejectsMalformedBaseDate() throws Exception {
        mockMvc.perform(authed(get("/analysis/categories").param("baseDate", "어제")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("baseDate"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/analysis/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
