package com.back.was.domain.dev;

import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.coaching.CoachingMessageRepository;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.CoachingStatus;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.report.GrowthReportRepository;
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
import java.util.List;

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
 * 뒤로 밀리는지, 과거 챌린지 4건이 문서의 결과(C2 만 FAILED)로 들어가는지, 넛지 판정이
 * 한 번 돌아 시나리오대로 카드가 생기는지.
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

    private static final long DELIVERY = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    ChallengeRepository challengeRepository;

    @Autowired
    GrowthReportRepository growthReportRepository;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CoachingMessageRepository coachingMessageRepository;

    @Autowired
    NudgeRepository nudgeRepository;

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

    private List<Challenge> myChallenges() {
        return challengeRepository.findAllByUserId(user.getId(), null);
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 DEV-01: DELIVERY_SPIKE 는 거래를 깔고 넛지를 만든다")
    void deliverySpikeCreatesANudge() throws Exception {
        mockMvc.perform(authed(post("/dev/seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario": "DELIVERY_SPIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scenario").value("DELIVERY_SPIKE"))
                .andExpect(jsonPath("$.data.transactionCount").value(Matchers.greaterThan(150)))
                .andExpect(jsonPath("$.data.nudgeCreated").value(true));
    }

    @Test
    @DisplayName("NORMAL 은 같은 과거 데이터에 평소 수준 이번 주 → 넛지 없음")
    void normalCreatesNoNudge() throws Exception {
        mockMvc.perform(authed(post("/dev/seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario": "NORMAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenario").value("NORMAL"))
                .andExpect(jsonPath("$.data.nudgeCreated").value(false));

        assertThat(nudgeRepository.findAll()).isEmpty();
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

    @Test
    @DisplayName("완료된 챌린지 4건이 문서의 결과로 들어간다 — C2 만 FAILED (KPI 75%)")
    void insertsFourFinishedChallenges() throws Exception {
        seed("DELIVERY_SPIKE");

        List<Challenge> challenges = myChallenges();
        assertThat(challenges).hasSize(4).allSatisfy(challenge ->
                assertThat(challenge.isFinished()).isTrue());
        assertThat(challenges).filteredOn(c -> c.getStatus() == ChallengeStatus.SUCCESS).hasSize(3);
        assertThat(challenges).filteredOn(c -> c.getStatus() == ChallengeStatus.FAILED)
                .singleElement()
                .satisfies(c2 -> {
                    assertThat(c2.getTitle()).isEqualTo("배달 2회 이하");
                    assertThat(c2.getSpendingCount()).isEqualTo(3);
                    assertThat(c2.getAchievementRate()).isEqualByComparingTo("150.00");
                });
    }

    @Test
    @DisplayName("진행 중 챌린지는 없다 — 시연에서 COACH-04 로 직접 만들어야 한다")
    void leavesNoRunningChallenge() throws Exception {
        seed("DELIVERY_SPIKE");

        assertThat(challengeRepository.findAllByUserId(user.getId(), ChallengeStatus.IN_PROGRESS))
                .isEmpty();
    }

    @Test
    @DisplayName("챌린지마다 코칭·메시지 2개·성장 리포트가 함께 들어간다")
    void insertsCoachingAndReportForEachChallenge() throws Exception {
        seed("DELIVERY_SPIKE");

        assertThat(coachingRepository.findAll()).hasSize(4).allSatisfy(coaching -> {
            assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.COMPLETED);
            assertThat(coaching.hasProposal()).isTrue();
            assertThat(coachingMessageRepository
                    .findByCoachingIdOrderByCreatedAtAscIdAsc(coaching.getId())).hasSize(2);
        });
        assertThat(growthReportRepository.findAll()).hasSize(4).allSatisfy(report ->
                assertThat(report.getFeedback()).isNotBlank());
    }

    @Test
    @DisplayName("과거 챌린지의 시각이 뒤로 밀려 있다 — CHL-01 의 최신 확정 순 정렬이 흐름과 맞는다")
    void backdatesTheChallengeTimestamps() throws Exception {
        seed("DELIVERY_SPIKE");
        entityManager.clear();

        // 목록은 createdAt 내림차순이라 가장 최근 확정(6주 전 C4)이 먼저 와야 한다
        assertThat(myChallenges()).first()
                .satisfies(newest -> assertThat(newest.getTitle()).isEqualTo("배달비 50,000원 이내"));
        assertThat(myChallenges()).last()
                .satisfies(oldest -> assertThat(oldest.getTitle()).isEqualTo("카페 30,000원 이내"));
        assertThat(myChallenges()).allSatisfy(challenge ->
                assertThat(challenge.getCreatedAt().toLocalDate()).isBefore(TODAY));
    }

    @Test
    @DisplayName("넛지는 배달에 한 장만 생긴다 (시연 6-1)")
    void createsOnlyTheDeliveryNudge() throws Exception {
        seed("DELIVERY_SPIKE");

        assertThat(nudgeRepository.findAll())
                .singleElement()
                .satisfies(nudge -> {
                    assertThat(nudge.getCategory().getId()).isEqualTo(DELIVERY);
                    assertThat(nudge.getSpendingAmount()).isEqualTo(101_000);
                    assertThat(nudge.getAvgAmount()).isEqualTo(60_000);
                    assertThat(nudge.getGrowthRate()).isEqualByComparingTo("68.33");
                    assertThat(nudge.getAnalysisStartDate()).isEqualTo(Weeks.mondayOf(TODAY));
                });
    }

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
            assertThat(myChallenges()).hasSize(4);
            assertThat(growthReportRepository.findAll()).hasSize(4);
            assertThat(coachingRepository.findAll()).hasSize(4);
        }

        @Test
        @DisplayName("시나리오를 바꿔 누르면 넛지도 따라 사라진다")
        void switchingScenarioRemovesTheNudge() throws Exception {
            seed("DELIVERY_SPIKE");
            assertThat(nudgeRepository.findAll()).hasSize(1);

            seed("NORMAL");

            assertThat(nudgeRepository.findAll()).isEmpty();
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

    @Test
    @DisplayName("시드 직후 홈·주간 분석·넛지·챌린지 목록이 시연값으로 응답한다 (시연 5장 2~4단계)")
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

        // 3단계 넛지 카드
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                .andExpect(jsonPath("$.data.spendingAmount").value(101_000))
                .andExpect(jsonPath("$.data.avgAmount").value(60_000))
                .andExpect(jsonPath("$.data.growthRate").value(68.33));

        // 완료 목록 4건
        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(4)));
    }
}
