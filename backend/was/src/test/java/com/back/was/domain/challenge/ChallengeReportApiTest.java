package com.back.was.domain.challenge;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHL-03 성장 리포트 + 챌린지 종료 판정 (서버_내부_처리 4장).
 *
 * <p>기준값은 명세 CHL-03 예시 그대로다: 목표 50,000원 / 기간 지출 35,000원 2회 /
 * {@code difference} 15,000 / {@code before} 107,000원 4회 / 70.00% / {@code SUCCESS}.
 * {@code before} 의 107,000원 4회는 시연_데이터 5장 9단계의 "챌린지 시작 전 7일
 * (10-28 ~ 11-03) 배달 합계"다.
 *
 * <p><b>이 클래스는 {@code @Transactional} 이 아니다.</b> 종료 판정이
 * {@code REQUIRES_NEW} 로 자기 트랜잭션을 열기 때문에, 테스트가 트랜잭션을 쥐고 있으면
 * 판정이 커밋한 결과를 테스트가 보지 못하거나 롤백되지 않는다. {@code NudgeTextApiTest} 와
 * 같은 이유로 실제로 커밋하고 {@code @AfterEach} 에서 SQL 로 치운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChallengeReportApiTest {

    private static final String EMAIL = "challenge-report@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    /** 어제 끝난 7일 챌린지 — 종료 판정 대상이다 ({@code END_DATE < 오늘}). */
    private static final LocalDate CHALLENGE_START = TODAY.minusDays(7);
    private static final LocalDate CHALLENGE_END = TODAY.minusDays(1);

    private static final long DELIVERY = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChallengeTerminationJob terminationJob;

    @Autowired
    ChallengeTermination challengeTermination;

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

    /** 커밋한 데이터를 자식 테이블부터 치운다 (FK 순서 — 서버_내부_처리 12장과 같은 순서). */
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

    /**
     * 명세 CHL-03 예시의 챌린지 — 어제 끝난 7일 배달 챌린지, 목표 50,000원.
     *
     * <p>기간 안 지출 35,000원 2회, 직전 7일 지출 107,000원 4회를 함께 깔아 둔다. 실적
     * 컬럼은 후처리 2번이 이미 채운 상태를 흉내낸다 — 판정은 저장된 실적을 읽는다.
     */
    private Long givenFinishedPeriodChallenge(User owner, GoalType goalType,
                                              Integer goalAmount, Integer goalCount,
                                              int spendingAmount, int spendingCount) {
        return transactionTemplate.execute(status -> {
            User persisted = userRepository.findById(owner.getId()).orElseThrow();
            Coaching coaching = coachingRepository.save(Coaching.start(persisted));
            coaching.cancel();
            Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();

            Challenge challenge = challengeRepository.save(Challenge.of(
                    coaching, delivery, "배달비 50,000원 이내", goalType, goalAmount, goalCount,
                    CHALLENGE_START, CHALLENGE_END));
            challenge.updatePerformance(spendingAmount, spendingCount);
            return challenge.getId();
        });
    }

    private Long givenDeliveryChallenge() {
        return givenFinishedPeriodChallenge(user, GoalType.AMOUNT, 50_000, null, 35_000, 2);
    }

    /** 챌린지 시작 직전 7일(= 같은 길이 기간)의 배달 지출 107,000원 4회 (시연 5장 9단계). */
    private void givenSpendingBeforeTheChallenge() {
        transactionTemplate.executeWithoutResult(status -> {
            User persisted = userRepository.findById(user.getId()).orElseThrow();
            Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();
            int[] amounts = {32_000, 25_000, 30_000, 20_000};   // 합계 107,000 / 4회

            for (int i = 0; i < amounts.length; i++) {
                transactionRepository.save(Transaction.of(
                        persisted, delivery, amounts[i], CHALLENGE_START.minusDays(i + 1), null));
            }
        });
    }

    // --- 종료 판정 (서버_내부_처리 4장) --------------------------------------

    @Test
    @DisplayName("종료일이 지난 챌린지를 판정하고 리포트를 만든다")
    void terminatesDueChallengesAndCreatesAReport() {
        Long challengeId = givenDeliveryChallenge();

        assertThat(terminationJob.terminateDue()).isEqualTo(1);

        assertThat(challengeRepository.findById(challengeId).orElseThrow().getStatus())
                .isEqualTo(ChallengeStatus.SUCCESS);   // 35,000 ≤ 50,000
        assertThat(growthReportRepository.existsByChallengeId(challengeId)).isTrue();
    }

    @Test
    @DisplayName("BEFORE_ 는 시작일 직전 같은 길이 기간이다 (7일 챌린지 → 직전 7일)")
    void storesTheSameLengthPeriodBeforeTheChallenge() {
        Long challengeId = givenDeliveryChallenge();
        givenSpendingBeforeTheChallenge();

        terminationJob.terminateDue();

        assertThat(growthReportRepository.findByChallengeIdAndUserId(challengeId, user.getId()))
                .get()
                .satisfies(report -> {
                    assertThat(report.getBeforeAmount()).isEqualTo(107_000);
                    assertThat(report.getBeforeCount()).isEqualTo(4);
                });
    }

    @Test
    @DisplayName("종료일 당일은 아직 판정하지 않는다 — 자정까지 기록이 더 들어올 수 있다")
    void doesNotTerminateOnTheLastDay() {
        Long challengeId = transactionTemplate.execute(status -> {
            User persisted = userRepository.findById(user.getId()).orElseThrow();
            Coaching coaching = coachingRepository.save(Coaching.start(persisted));
            coaching.cancel();
            Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();

            return challengeRepository.save(Challenge.of(
                    coaching, delivery, "오늘 끝나는 챌린지", GoalType.AMOUNT, 50_000, null,
                    TODAY.minusDays(6), TODAY)).getId();
        });

        assertThat(terminationJob.terminateDue()).isZero();
        assertThat(challengeRepository.findById(challengeId).orElseThrow().getStatus())
                .isEqualTo(ChallengeStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("두 번 돌려도 리포트는 하나다 — 판정은 되돌릴 수 없다")
    void terminatingTwiceIsSafe() {
        Long challengeId = givenDeliveryChallenge();

        terminationJob.terminateDue();
        assertThat(terminationJob.terminateDue()).isZero();
        challengeTermination.terminate(challengeId);   // 직접 불러도 안전해야 한다

        assertThat(growthReportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("목표를 넘겼으면 FAILED 로 판정하고 실패 문구를 넣는다")
    void failedChallengeGetsFailureFeedback() {
        Long challengeId = givenFinishedPeriodChallenge(
                user, GoalType.AMOUNT, 50_000, null, 56_000, 3);

        terminationJob.terminateDue();

        assertThat(challengeRepository.findById(challengeId).orElseThrow().getStatus())
                .isEqualTo(ChallengeStatus.FAILED);
        assertThat(growthReportRepository.findByChallengeIdAndUserId(challengeId, user.getId()))
                .get()
                .satisfies(report -> assertThat(report.getFeedback()).isNotBlank());
    }

    // --- CHL-03 응답 --------------------------------------------------------

    @Test
    @DisplayName("명세 CHL-03: 리포트와 챌린지 값을 합쳐서 준다")
    void returnsTheReport() throws Exception {
        Long challengeId = givenDeliveryChallenge();
        givenSpendingBeforeTheChallenge();
        terminationJob.terminateDue();

        mockMvc.perform(authed(get("/challenges/{id}/report", challengeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.challengeId").value(challengeId))
                .andExpect(jsonPath("$.data.title").value("배달비 50,000원 이내"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.goalType").value("AMOUNT"))
                .andExpect(jsonPath("$.data.goalAmount").value(50_000))
                .andExpect(jsonPath("$.data.spendingAmount").value(35_000))
                // 목표 − 실적. 양수 = 목표보다 덜 씀 (명세 CHL-03 출처 표)
                .andExpect(jsonPath("$.data.difference").value(15_000))
                .andExpect(jsonPath("$.data.achievementRate").value(70.00))
                .andExpect(jsonPath("$.data.before.amount").value(107_000))
                .andExpect(jsonPath("$.data.before.count").value(4))
                .andExpect(jsonPath("$.data.during.amount").value(35_000))
                .andExpect(jsonPath("$.data.during.count").value(2))
                .andExpect(jsonPath("$.data.feedback").value(Matchers.not(Matchers.emptyString())))
                .andExpect(jsonPath("$.data.createdAt").value(Matchers.containsString("+09:00")));
    }

    @Test
    @DisplayName("목표를 넘겼으면 difference 가 음수다 — 부호가 넘겼는지를 말한다")
    void differenceIsNegativeWhenOverTheGoal() throws Exception {
        Long challengeId = givenFinishedPeriodChallenge(
                user, GoalType.AMOUNT, 50_000, null, 56_000, 3);
        terminationJob.terminateDue();

        mockMvc.perform(authed(get("/challenges/{id}/report", challengeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.difference").value(-6_000));
    }

    @Test
    @DisplayName("COUNT 챌린지는 goalCount 와 횟수 기준 difference 를 준다 (명세 예시에 없는 키)")
    void countGoalReportCarriesTheCountGoal() throws Exception {
        Long challengeId = givenFinishedPeriodChallenge(
                user, GoalType.COUNT, null, 2, 35_000, 3);
        terminationJob.terminateDue();

        mockMvc.perform(authed(get("/challenges/{id}/report", challengeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalType").value("COUNT"))
                .andExpect(jsonPath("$.data.goalCount").value(2))
                .andExpect(jsonPath("$.data.goalAmount").value(Matchers.nullValue()))
                // 2회 목표에 3회 → −1. 금액과 섞이지 않는다
                .andExpect(jsonPath("$.data.difference").value(-1))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    // --- 404 세 가지 (명세 CHL-03 Errors) ------------------------------------

    @Nested
    @DisplayName("리포트가 없는 경우")
    class NotFound {

        @Test
        @DisplayName("진행 중 챌린지는 404 — 리포트가 아직 없다")
        void inProgressChallengeHasNoReport() throws Exception {
            Long challengeId = givenDeliveryChallenge();   // 판정 전

            mockMvc.perform(authed(get("/challenges/{id}/report", challengeId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("없는 챌린지·남의 챌린지·진행 중이 모두 같은 404 다 (명세 0장 5))")
        void allThreeCasesLookIdentical() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "challenge-report-other@email.com", "irrelevant-hash"));
            Long othersChallengeId = givenFinishedPeriodChallenge(
                    other, GoalType.AMOUNT, 50_000, null, 35_000, 2);
            Long inProgressId = givenDeliveryChallenge();
            terminationJob.terminateDue();   // 남의 챌린지에는 리포트가 생긴다

            String missing = mockMvc.perform(authed(get("/challenges/{id}/report", 999_999)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            String others = mockMvc.perform(authed(get("/challenges/{id}/report", othersChallengeId)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            // inProgressId 도 판정됐으므로 리포트가 있다 — 그래서 여기서는 쓰지 않는다
            assertThat(inProgressId).isNotNull();
            assertThat(others).isEqualTo(missing);
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            Long challengeId = givenDeliveryChallenge();

            mockMvc.perform(get("/challenges/{id}/report", challengeId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
