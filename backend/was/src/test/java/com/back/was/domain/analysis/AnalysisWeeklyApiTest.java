package com.back.was.domain.analysis;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
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

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ANL-02 주간 지출 추이.
 *
 * <p>기대 금액은 docs/시연_데이터.md 6-2 (147,500 / 166,500 / 144,500 / 116,150)이고,
 * 날짜는 실행 시점의 KST 주 경계로 잡는다 — "이번 주"가 들어가는 API 라 고정 날짜로는
 * 검증이 성립하지 않는다. 각 주의 금액은 그 주 <b>월요일</b> 한 건에 몰아 넣는다(월요일은
 * 항상 오늘 이하라 미래 날짜가 되지 않는다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisWeeklyApiTest {

    private static final String EMAIL = "anl-weekly@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate THIS_MONDAY = Weeks.mondayOf(TODAY);

    private static final long DELIVERY = 2L;
    private static final long SALARY = 7L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

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

    /** 시연 6-2 의 네 주 — 각 주 월요일에 한 건씩. */
    private void givenFourWeeks() {
        given(user, DELIVERY, 147_500, THIS_MONDAY.minusWeeks(3));
        given(user, DELIVERY, 166_500, THIS_MONDAY.minusWeeks(2));
        given(user, DELIVERY, 144_500, THIS_MONDAY.minusWeeks(1));
        given(user, DELIVERY, 116_150, THIS_MONDAY);
    }

    private void given(User owner, long categoryId, int amount, LocalDate date) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        transactionRepository.save(Transaction.of(owner, category, amount, date, null));
    }

    private static String weekStart(int weeksAgo) {
        return THIS_MONDAY.minusWeeks(weeksAgo).toString();
    }

    private static String weekEnd(int weeksAgo) {
        return THIS_MONDAY.minusWeeks(weeksAgo).plusDays(6).toString();
    }

    // --- 기본 동작 ----------------------------------------------------------

    @Test
    @DisplayName("시연 6-2: 기본 4주를 오래된 주부터 — 147,500 / 166,500 / 144,500 / 116,150")
    void fourWeeksByDefault() throws Exception {
        givenFourWeeks();

        mockMvc.perform(authed(get("/analysis/weekly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weeks.length()").value(4))
                .andExpect(jsonPath("$.data.weeks[0].weekStart").value(weekStart(3)))
                .andExpect(jsonPath("$.data.weeks[0].weekEnd").value(weekEnd(3)))
                .andExpect(jsonPath("$.data.weeks[0].totalExpense").value(147_500))
                .andExpect(jsonPath("$.data.weeks[1].totalExpense").value(166_500))
                .andExpect(jsonPath("$.data.weeks[2].totalExpense").value(144_500))
                .andExpect(jsonPath("$.data.weeks[3].weekStart").value(weekStart(0)))
                .andExpect(jsonPath("$.data.weeks[3].totalExpense").value(116_150));
    }

    @Test
    @DisplayName("weekStart 는 월요일, weekEnd 는 일요일 — 이번 주 막대도 오늘이 아니라 일요일로 끝난다 (명세 예시)")
    void currentWeekIsLabelledThroughSunday() throws Exception {
        givenFourWeeks();

        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks.length()").value(1))
                .andExpect(jsonPath("$.data.weeks[0].weekStart").value(THIS_MONDAY.toString()))
                .andExpect(jsonPath("$.data.weeks[0].weekEnd")
                        .value(THIS_MONDAY.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.weeks[0].totalExpense").value(116_150));
    }

    @Test
    @DisplayName("라벨은 일요일까지지만 합계는 오늘까지다 — 이번 주 남은 날짜의 거래는 더하지 않는다")
    void currentWeekSumStopsAtToday() throws Exception {
        DateRange thisWeek = Weeks.weekOf(TODAY);
        assumeTrue(TODAY.isBefore(thisWeek.end()), "오늘이 일요일이면 이번 주에 남은 날짜가 없다");

        givenFourWeeks();
        // TX-01 이라면 미래 날짜라 거부되는 행이다. 합계 범위(월~오늘)와 라벨 범위(월~일)가
        // 다르다는 결정을 고정하려고 리포지터리로 직접 넣는다.
        given(user, DELIVERY, 999_999, thisWeek.end());

        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks[0].weekEnd").value(thisWeek.end().toString()))
                .andExpect(jsonPath("$.data.weeks[0].totalExpense").value(116_150));
    }

    @Test
    @DisplayName("거래가 없는 주도 0원 막대로 들어간다 — 차트의 x축이 비면 안 된다")
    void emptyWeeksAreZeroNotMissing() throws Exception {
        given(user, DELIVERY, 116_150, THIS_MONDAY);

        mockMvc.perform(authed(get("/analysis/weekly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks.length()").value(4))
                .andExpect(jsonPath("$.data.weeks[0].totalExpense").value(0))
                .andExpect(jsonPath("$.data.weeks[1].totalExpense").value(0))
                .andExpect(jsonPath("$.data.weeks[2].totalExpense").value(0))
                .andExpect(jsonPath("$.data.weeks[3].totalExpense").value(116_150));
    }

    @Test
    @DisplayName("weeks 를 지정하면 그 개수만큼 (이번 주 포함)")
    void respectsRequestedWeekCount() throws Exception {
        givenFourWeeks();

        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks.length()").value(2))
                .andExpect(jsonPath("$.data.weeks[0].weekStart").value(weekStart(1)))
                .andExpect(jsonPath("$.data.weeks[0].totalExpense").value(144_500))
                .andExpect(jsonPath("$.data.weeks[1].totalExpense").value(116_150));
    }

    // --- 제외 규칙 ----------------------------------------------------------

    @Test
    @DisplayName("월세는 항상 제외한다 (ERD 4장 · 화면 '이번 주 지출(월세 제외)')")
    void rentIsAlwaysExcluded() throws Exception {
        givenFourWeeks();
        given(user, RentCategory.ID, 550_000, THIS_MONDAY);

        mockMvc.perform(authed(get("/analysis/weekly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks[3].totalExpense").value(116_150));
    }

    @Test
    @DisplayName("수입은 지출 추이에 들어가지 않는다")
    void incomeIsExcluded() throws Exception {
        givenFourWeeks();
        given(user, SALARY, 2_400_000, THIS_MONDAY);

        mockMvc.perform(authed(get("/analysis/weekly")))
                .andExpect(jsonPath("$.data.weeks[3].totalExpense").value(116_150));
    }

    @Test
    @DisplayName("다른 사용자의 거래는 섞이지 않는다")
    void scopedByUser() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "anl-weekly-other@email.com", "irrelevant-hash"));
        given(other, DELIVERY, 500_000, THIS_MONDAY);
        given(user, DELIVERY, 116_150, THIS_MONDAY);

        mockMvc.perform(authed(get("/analysis/weekly")))
                .andExpect(jsonPath("$.data.weeks[3].totalExpense").value(116_150));
    }

    // --- 검증·인증 ----------------------------------------------------------

    @Test
    @DisplayName("weeks 가 1 미만이거나 52 를 넘으면 400 VALIDATION_ERROR (조용히 깎지 않는다)")
    void rejectsWeeksOutOfRange() throws Exception {
        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("weeks"));

        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "53")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("weeks 가 숫자가 아니면 400 VALIDATION_ERROR")
    void rejectsNonNumericWeeks() throws Exception {
        mockMvc.perform(authed(get("/analysis/weekly").param("weeks", "네주")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/analysis/weekly"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
