package com.back.was.domain.dev;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.report.GrowthReportRepository;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEV-02 챌린지 강제 종료.
 *
 * <p>기준값은 시연_데이터 5장 8단계 그대로다: 목표 50,000원 / 확정 직후 실적 24,000원 →
 * {@code SUCCESS} 는 11,000원을 보태 <b>35,000원</b>, {@code FAILED} 는 32,000원을 보태
 * <b>56,000원</b>.
 *
 * <p>{@code @ActiveProfiles("dev")} 가 필요하다 — 컨트롤러와 서비스가 그 프로필에서만
 * 등록된다. 프로필 없이도 막히는지는 {@code DevProfileOnlyTest} 가 확인한다.
 *
 * <p>{@code @Transactional} 이 아니다. 판정이 {@code REQUIRES_NEW} 로 자기 트랜잭션을
 * 열기 때문에 테스트가 트랜잭션을 쥐면 결과를 보지 못한다 — {@code ChallengeReportApiTest}
 * 와 같은 이유로 실제 커밋 후 SQL 로 치운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevChallengeApiTest {

    private static final String EMAIL = "dev-challenge@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    private static final long DELIVERY = 2L;

    /** 시연 5장 7단계: 목표 50,000원, 오늘 시작하는 7일 챌린지. */
    private static final int GOAL_AMOUNT = 50_000;

    /** 확정 직후 실적 — 오늘 기록한 치킨 24,000원 1건. */
    private static final int INITIAL_SPENDING = 24_000;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChallengeRepository challengeRepository;

    @Autowired
    GrowthReportRepository growthReportRepository;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

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

    /** 커밋한 데이터를 자식 테이블부터 치운다 (서버_내부_처리 12장의 DEV-01 삭제 순서). */
    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from growth_reports").executeUpdate();
            entityManager.createNativeQuery("delete from challenges").executeUpdate();
            entityManager.createNativeQuery("delete from coaching_messages").executeUpdate();
            entityManager.createNativeQuery("delete from coachings").executeUpdate();
            entityManager.createNativeQuery("delete from transactions").executeUpdate();
            entityManager.createNativeQuery("delete from nudges").executeUpdate();
            entityManager.createNativeQuery("delete from refresh_token").executeUpdate();
            entityManager.createNativeQuery("delete from users").executeUpdate();
        });
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private MockHttpServletRequestBuilder completeRequest(Object challengeId, String body) {
        MockHttpServletRequestBuilder request =
                authed(post("/dev/challenges/{id}/complete", challengeId));
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /**
     * 시연 5장 7단계의 상태를 만든다 — 오늘 확정한 7일 배달 챌린지, 오늘 기록한 24,000원
     * 1건이 이미 실적에 반영된 상태.
     */
    private Long givenRunningChallenge(User owner, GoalType goalType,
                                       Integer goalAmount, Integer goalCount) {
        return transactionTemplate.execute(status -> {
            User persisted = userRepository.findById(owner.getId()).orElseThrow();
            Coaching coaching = coachingRepository.save(Coaching.start(persisted));
            coaching.cancel();
            Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();

            transactionRepository.save(Transaction.of(
                    persisted, delivery, INITIAL_SPENDING, TODAY, "치킨"));
            Challenge challenge = challengeRepository.save(Challenge.of(
                    coaching, delivery, "배달비 50,000원 이내", goalType, goalAmount, goalCount,
                    TODAY, TODAY.plusDays(6)));
            challenge.updatePerformance(INITIAL_SPENDING, 1);
            return challenge.getId();
        });
    }

    private Long givenDeliveryChallenge() {
        return givenRunningChallenge(user, GoalType.AMOUNT, GOAL_AMOUNT, null);
    }

    private Challenge reload(Long challengeId) {
        return challengeRepository.findById(challengeId).orElseThrow();
    }

    // --- 세 가지 버튼 (명세 DEV-02 표) --------------------------------------

    @Test
    @DisplayName("SUCCESS 시나리오: 11,000원을 보태 35,000원 → SUCCESS (시연 5장 8단계)")
    void successScenarioReachesSeventyPercentOfTheGoal() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "SUCCESS"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.spendingAmount").value(35_000))
                .andExpect(jsonPath("$.data.during.amount").value(35_000))
                .andExpect(jsonPath("$.data.during.count").value(2))
                // 목표 50,000 − 실적 35,000 = 15,000원 덜 씀 (시연 9단계)
                .andExpect(jsonPath("$.data.difference").value(15_000))
                .andExpect(jsonPath("$.data.achievementRate").value(70.00));

        assertThat(reload(challengeId).getStatus()).isEqualTo(ChallengeStatus.SUCCESS);
    }

    @Test
    @DisplayName("FAILED 시나리오: 32,000원을 보태 56,000원 → FAILED (시연 5장 8단계 각주)")
    void failedScenarioExceedsTheGoal() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "FAILED"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.spendingAmount").value(56_000))
                .andExpect(jsonPath("$.data.difference").value(-6_000))
                .andExpect(jsonPath("$.data.achievementRate").value(112.00));
    }

    @Test
    @DisplayName("scenario 없이 보내면 현재 기록 그대로 판정한다 ('기간 종료 · 결과 보기')")
    void withoutAScenarioJudgesAsIs() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, "{}"))
                .andExpect(status().isOk())
                // 24,000 ≤ 50,000 이라 SUCCESS, 거래는 늘지 않는다
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.spendingAmount").value(INITIAL_SPENDING))
                .andExpect(jsonPath("$.data.during.count").value(1));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("본문 없이 POST 해도 된다 — scenario 가 선택이라 보낼 값이 없는 요청이 정상이다")
    void acceptsAnEmptyBody() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    // --- 연결 (서버_내부_처리 4장·12장) --------------------------------------

    @Test
    @DisplayName("추가한 거래는 TX-01 경로로 들어간다 — 후처리 2번이 돌아 실적이 즉시 갱신된다")
    void addedTransactionGoesThroughPostProcessing() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "SUCCESS"}
                        """))
                .andExpect(status().isOk());

        // 리포지터리로 직접 넣었다면 실적이 24,000 에 머물고 판정이 옛 숫자를 읽는다
        assertThat(reload(challengeId).getSpendingAmount()).isEqualTo(35_000);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("응답은 CHL-03 과 같은 형식이다 — 버튼을 누르면 바로 리포트 화면을 그린다")
    void returnsTheGrowthReport() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "SUCCESS"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").value(challengeId))
                .andExpect(jsonPath("$.data.title").value("배달비 50,000원 이내"))
                .andExpect(jsonPath("$.data.goalType").value("AMOUNT"))
                .andExpect(jsonPath("$.data.goalAmount").value(GOAL_AMOUNT))
                .andExpect(jsonPath("$.data.before.amount").value(0))
                .andExpect(jsonPath("$.data.feedback").value(Matchers.not(Matchers.emptyString())))
                .andExpect(jsonPath("$.data.createdAt").value(Matchers.containsString("+09:00")));

        assertThat(growthReportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번 눌러도 결과가 바뀌지 않는다 — 판정은 되돌릴 수 없다 (ERD 9장)")
    void pressingTwiceKeepsTheFirstVerdict() throws Exception {
        Long challengeId = givenDeliveryChallenge();

        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "SUCCESS"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // 두 번째는 FAILED 시나리오로 눌러도 이미 종료된 챌린지라 거래도 늘지 않는다
        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "FAILED"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.spendingAmount").value(35_000));

        assertThat(growthReportRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("횟수 목표의 FAILED 는 건수를 목표보다 한 건 넘긴다")
    void countGoalFailedScenarioExceedsTheCount() throws Exception {
        Long challengeId = givenRunningChallenge(user, GoalType.COUNT, null, 2);

        mockMvc.perform(completeRequest(challengeId, """
                        {"scenario": "FAILED"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.goalCount").value(2))
                .andExpect(jsonPath("$.data.during.count").value(3))
                .andExpect(jsonPath("$.data.difference").value(-1));
    }

    // --- 접근 범위 ----------------------------------------------------------

    @Nested
    @DisplayName("접근 범위")
    class Access {

        @Test
        @DisplayName("없는 챌린지와 남의 챌린지는 똑같이 404 다 (명세 0장 5))")
        void unknownOrOthersChallengeIsNotFound() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "dev-challenge-other@email.com", "irrelevant-hash"));
            Long othersChallengeId = givenRunningChallenge(other, GoalType.AMOUNT, GOAL_AMOUNT, null);

            String missing = mockMvc.perform(completeRequest(999_999, "{}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();
            String others = mockMvc.perform(completeRequest(othersChallengeId, "{}"))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            assertThat(others).isEqualTo(missing);
            // 남의 챌린지는 건드려지지 않는다
            assertThat(reload(othersChallengeId).getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("없는 scenario 값이면 400 VALIDATION_ERROR")
        void rejectsAnUnknownScenario() throws Exception {
            Long challengeId = givenDeliveryChallenge();

            mockMvc.perform(completeRequest(challengeId, """
                            {"scenario": "FOO"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("IN_PROGRESS 는 보낼 수 없다 — 강제 종료 API 에 의미가 없는 요청이다")
        void rejectsInProgressAsAScenario() throws Exception {
            Long challengeId = givenDeliveryChallenge();

            mockMvc.perform(completeRequest(challengeId, """
                            {"scenario": "IN_PROGRESS"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED — /dev/** 도 토큰이 필요하다")
        void requiresAuthentication() throws Exception {
            Long challengeId = givenDeliveryChallenge();

            mockMvc.perform(post("/dev/challenges/{id}/complete", challengeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
