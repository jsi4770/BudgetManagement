package com.back.was.domain.dev;

import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEV-01 시연 데이터 초기화.
 *
 * <p>거래 생성 규칙 자체는 {@code DemoTransactionPlannerTest} 가 문서의 CSV 와 비교해
 * 검증한다. 여기서 보는 것은 <b>영속화와 순서</b>다: 기존 데이터가 지워지는지, 가입일이
 * 뒤로 밀리는지, 예산이 직전 3개월만 들어가는지.
 *
 * <p>시연_데이터 12장이 요구하는 나머지 두 단계 — <b>과거 코칭·챌린지 4건</b>과
 * <b>이번 주 넛지 판정 1회</b> — 는 해당 도메인이 미구현이라 아직 검증할 것이 없다.
 *
 * <p>{@code @Transactional} 로 돈다 — 시드가 한 트랜잭션이고 비동기 후처리를 거치지 않아서
 * 테스트 트랜잭션 안에서 결과를 그대로 볼 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DevSeedApiTest {

    private static final String EMAIL = "dev-seed@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

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

    private String seed(String scenario) throws Exception {
        return mockMvc.perform(authed(post("/dev/seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario": "%s"}
                                """.formatted(scenario)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // --- 응답 ---------------------------------------------------------------

    /**
     * 응답에 {@code nudgeCreated} 가 <b>아직 없다</b> — 넛지 도메인이 미구현이라 시드가 거래와
     * 예산만 깔고 끝난다. 판정을 붙이면 {@code SeedResponse} 에 그 필드를 되살리고
     * {@code DELIVERY_SPIKE} 는 {@code true} 를 단정해야 한다.
     */
    @Test
    @DisplayName("명세 DEV-01: DELIVERY_SPIKE 는 거래를 깐다")
    void deliverySpikeSeedsTransactions() throws Exception {
        mockMvc.perform(authed(post("/dev/seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario": "DELIVERY_SPIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scenario").value("DELIVERY_SPIKE"))
                .andExpect(jsonPath("$.data.transactionCount").value(Matchers.greaterThan(150)))
                .andExpect(jsonPath("$.data.nudgeCreated").doesNotExist());
    }

    @Test
    @DisplayName("NORMAL 도 같은 과거 데이터에 평소 수준 이번 주를 깐다")
    void normalSeedsTransactions() throws Exception {
        mockMvc.perform(authed(post("/dev/seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario": "NORMAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenario").value("NORMAL"))
                .andExpect(jsonPath("$.data.transactionCount").value(Matchers.greaterThan(150)));
    }

    @Test
    @DisplayName("transactionCount 는 실제로 저장된 건수와 같다")
    void transactionCountMatchesWhatWasStored() throws Exception {
        String body = seed("DELIVERY_SPIKE");
        int reported = ((Number) JsonPath.read(body, "$.data.transactionCount")).intValue();

        assertThat(transactionRepository.count()).isEqualTo(reported);
    }

    // --- 심은 데이터 (시연_데이터 1·2·4장) ----------------------------------

    @Test
    @DisplayName("가입일을 D − 150일로 밀어 둔다 — 없으면 넛지 판정이 '데이터 부족'으로 생략된다")
    void backdatesTheSignUpDate() throws Exception {
        seed("DELIVERY_SPIKE");
        entityManager.clear();

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCreatedAt().toLocalDate())
                .isEqualTo(TODAY.minusDays(150));
    }

    @Test
    @DisplayName("예산은 직전 3개월만 — 이번 달은 BUDGET-01 의 자동 복사가 만들게 둔다")
    void insertsThreeMonthsOfBudgetsButNotThisMonth() throws Exception {
        seed("DELIVERY_SPIKE");

        assertThat(budgetRepository.findAll()).hasSize(3);
        assertThat(budgetRepository.findByUserIdAndYearMonth(
                user.getId(), java.time.YearMonth.from(TODAY).toString())).isEmpty();
        assertThat(budgetRepository.findByUserIdAndYearMonth(
                user.getId(), java.time.YearMonth.from(TODAY.minusMonths(1)).toString()))
                .get()
                .satisfies(budget -> assertThat(budget.getMonthlyBudget()).isEqualTo(1_200_000));
    }

    /*
     * 코칭·챌린지·넛지가 구현되면 여기에 네 가지를 더해야 한다 (기대값은 시연_데이터 4장,
     * 자세한 내용은 docs/AI_코칭_소비분석_인수인계.md 6장):
     *
     *   과거 챌린지 4건이 문서의 결과로 들어가는지 — C2 만 FAILED, 달성률 150.00 (KPI 75%)
     *   진행 중 챌린지가 0건인지 — 하나라도 있으면 시연의 COACH-04 확정이 409 로 막힌다
     *   챌린지마다 코칭 1건 + 메시지 2건이 함께 들어가는지
     *   코칭 20:00 / 챌린지 20:05 로 시각이 과거에 박히는지 (CHL-01 의 최신 확정 순 정렬)
     *   배달 넛지가 한 장 생기는지 — 101,000 / 평균 60,000 / +68.33% / 이번 주 월요일
     */

    // --- 재실행 (명세 DEV-01 "전체 삭제 후 삽입") ---------------------------

    @Nested
    @DisplayName("다시 누르면")
    class Reseeding {

        @Test
        @DisplayName("이전 데이터를 지우고 다시 깐다 — 건수가 두 배가 되지 않는다")
        void replacesInsteadOfAppending() throws Exception {
            String first = seed("DELIVERY_SPIKE");
            int firstCount = ((Number) JsonPath.read(first, "$.data.transactionCount")).intValue();

            String second = seed("DELIVERY_SPIKE");
            int secondCount = ((Number) JsonPath.read(second, "$.data.transactionCount")).intValue();

            assertThat(secondCount).isEqualTo(firstCount);
            assertThat(transactionRepository.count()).isEqualTo(firstCount);
            // 과거 챌린지·코칭이 4건으로 유지되는지도 여기서 봤었다 (인수인계)
        }

        @Test
        @DisplayName("다른 사용자의 데이터는 지우지 않는다")
        void doesNotTouchOtherUsers() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "dev-seed-other@email.com", "irrelevant-hash"));
            budgetRepository.save(com.back.was.domain.budget.Budget.of(
                    other, java.time.YearMonth.from(TODAY), 999_999, 0, null));

            seed("DELIVERY_SPIKE");

            assertThat(budgetRepository.findByUserIdAndYearMonth(
                    other.getId(), java.time.YearMonth.from(TODAY).toString())).isPresent();
        }
    }

    // --- 검증·접근 범위 ------------------------------------------------------

    @Nested
    @DisplayName("요청 검증과 접근 범위")
    class Validation {

        @Test
        @DisplayName("scenario 는 필수다 — 어느 데이터를 깔지가 요청의 전부다")
        void scenarioIsRequired() throws Exception {
            mockMvc.perform(authed(post("/dev/seed"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("scenario"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
        }

        @Test
        @DisplayName("없는 시나리오 값이면 400 VALIDATION_ERROR")
        void rejectsAnUnknownScenario() throws Exception {
            mockMvc.perform(authed(post("/dev/seed"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"scenario": "FOO"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/dev/seed")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"scenario": "NORMAL"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    // --- 시연 흐름이 실제로 돌아가는지 --------------------------------------

    /**
     * 시연 3단계(넛지 카드, {@code GET /nudges/current})는 <b>엔드포인트 자체가 없다</b> —
     * NDG-01~03 이 별도 담당자의 작업으로 넘어갔다. 돌아오면 아래를 되살릴 것:
     * <pre>
     * mockMvc.perform(authed(get("/nudges/current")))
     *         .andExpect(status().isOk())
     *         .andExpect(jsonPath("$.data.category.name").value("배달"))
     *         .andExpect(jsonPath("$.data.spendingAmount").value(101_000))
     *         .andExpect(jsonPath("$.data.avgAmount").value(60_000))
     *         .andExpect(jsonPath("$.data.growthRate").value(68.33));
     * </pre>
     */
    @Test
    @DisplayName("시드 직후 홈·주간 분석이 시연값으로 응답한다 (시연 5장 2·4단계)")
    void theDemoScreensWorkRightAfterSeeding() throws Exception {
        seed("DELIVERY_SPIKE");

        // 2단계 홈: 이번 달 급여 한 번. 총지출(시연값 666,150)은 D = 11-04 전용이라
        // 확인하지 않는다 — 달의 경과 일수에 따라 달라지고, 시연_데이터 6장도 그 날짜
        // 기준이라고 못박았다. 요일과 무관하게 고정인 값만 본다.
        mockMvc.perform(authed(get("/analysis/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIncome").value(2_400_000));

        // 4단계 주간 분석: 이번 주 지출(월세 제외) 116,150원 — 요일 보정 덕에 고정이다
        mockMvc.perform(authed(get("/analysis/weekly")).param("weeks", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks[0].totalExpense").value(116_150));

        // 완료 챌린지 목록 4건(CHL-01)도 여기서 봤었다 — 코칭·챌린지 인수인계로 빠졌다.
    }
}
