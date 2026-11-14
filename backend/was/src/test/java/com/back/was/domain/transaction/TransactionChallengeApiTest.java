package com.back.was.domain.transaction;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래 변경 후처리 <b>2번</b> 연결 (서버_내부_처리 1장 순서 2번) — TX-01·05·06 을 호출하면
 * 진행 중 챌린지의 실적이 실제로 움직이는지.
 *
 * <p>비율 계산과 덮어쓰기 규칙 자체는 {@code ChallengeTest.UpdatePerformance} 가 검증한다.
 * 여기서 보는 것은 <b>연결</b>이다: 세 엔드포인트가 같은 트랜잭션에서 재집계를 돌리는지,
 * TX-05 가 <b>수정 전 날짜</b>까지 재계산하는지, 기간·카테고리·사용자 범위를 지키는지.
 *
 * <p>기준값은 docs/시연_데이터.md 5장 7단계다: 챌린지 시작일이 오늘이라 오늘 기록한 치킨
 * 24,000원이 실적에 들어간다 (목표 50,000원 → 48.00%).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionChallengeApiTest {

    private static final String EMAIL = "tx-challenge@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    /** 수입 카테고리 — ERD 4장 시드의 급여. */
    private static final long SALARY = 8L;

    /** 시연 5장의 배달 챌린지 목표. */
    private static final int GOAL_AMOUNT = 50_000;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChallengeRepository challengeRepository;

    @Autowired
    CoachingRepository coachingRepository;

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

    /** 오늘 ~ 오늘+6 의 7일 챌린지. 확정 경로(COACH-04)와 같은 모양으로 만든다. */
    private Challenge givenChallenge(User owner, long categoryId, LocalDate start, LocalDate end) {
        Coaching coaching = coachingRepository.save(Coaching.start(owner));
        coaching.cancel();
        Category category = categoryRepository.findById(categoryId).orElseThrow();

        return challengeRepository.save(Challenge.of(
                coaching, category, "배달 50,000원 이내", GoalType.AMOUNT, GOAL_AMOUNT, null,
                start, end));
    }

    private Challenge givenDeliveryChallenge() {
        return givenChallenge(user, DELIVERY, TODAY, TODAY.plusDays(6));
    }

    /** TX-01 로 거래를 등록하고 ID 를 돌려준다 — 후처리가 도는 경로가 이쪽이다. */
    private long createTransaction(long categoryId, int amount, LocalDate date) throws Exception {
        String body = mockMvc.perform(authed(post("/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %d, "transactionDate": "%s"}
                                """.formatted(categoryId, amount, date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private Challenge reload(Challenge challenge) {
        return challengeRepository.findById(challenge.getId()).orElseThrow();
    }

    // --- TX-01 등록 ---------------------------------------------------------

    @Test
    @DisplayName("TX-01: 거래를 등록하면 실적이 즉시 올라간다 (시연 5장 7단계: 치킨 24,000원 → 48.00%)")
    void createUpdatesThePerformance() throws Exception {
        Challenge challenge = givenDeliveryChallenge();

        createTransaction(DELIVERY, 24_000, TODAY);

        assertThat(reload(challenge)).satisfies(updated -> {
            assertThat(updated.getSpendingAmount()).isEqualTo(24_000);
            assertThat(updated.getSpendingCount()).isEqualTo(1);
            assertThat(updated.getAchievementRate()).isEqualByComparingTo("48.00");
        });
    }

    @Test
    @DisplayName("여러 건이 쌓이면 합계와 건수가 함께 올라간다")
    void accumulatesAcrossTransactions() throws Exception {
        Challenge challenge = givenDeliveryChallenge();

        // TX-01 이 미래 날짜를 거부하므로(@NotFutureDate) 두 건 모두 오늘이다
        createTransaction(DELIVERY, 24_000, TODAY);
        createTransaction(DELIVERY, 11_000, TODAY);

        assertThat(reload(challenge)).satisfies(updated -> {
            assertThat(updated.getSpendingAmount()).isEqualTo(35_000);
            assertThat(updated.getSpendingCount()).isEqualTo(2);
            assertThat(updated.getAchievementRate()).isEqualByComparingTo("70.00");
        });
    }

    @Test
    @DisplayName("목표를 넘겨도 진행 중이다 — 자동 실패·취소는 명세에 없다 (ERD 9장)")
    void exceedingTheGoalDoesNotEndTheChallenge() throws Exception {
        Challenge challenge = givenDeliveryChallenge();

        createTransaction(DELIVERY, 56_000, TODAY);

        assertThat(reload(challenge)).satisfies(updated -> {
            assertThat(updated.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
            assertThat(updated.getAchievementRate()).isEqualByComparingTo("112.00");
        });
    }

    // --- 범위 (서버_내부_처리 2장) -------------------------------------------

    @Nested
    @DisplayName("재계산 대상의 범위")
    class Scope {

        @Test
        @DisplayName("기간 밖의 거래는 실적에 들어가지 않는다 (SRS 7.4-2)")
        void ignoresTransactionsOutsideThePeriod() throws Exception {
            Challenge challenge = givenDeliveryChallenge();

            // 챌린지는 오늘부터다 — 어제 거래는 기간 밖이다
            createTransaction(DELIVERY, 24_000, TODAY.minusDays(1));

            assertThat(reload(challenge).getSpendingAmount()).isZero();
        }

        @Test
        @DisplayName("다른 카테고리의 거래는 실적에 들어가지 않는다")
        void ignoresOtherCategories() throws Exception {
            Challenge challenge = givenDeliveryChallenge();

            createTransaction(FOOD, 24_000, TODAY);

            assertThat(reload(challenge).getSpendingAmount()).isZero();
        }

        @Test
        @DisplayName("수입 거래는 아무 챌린지에도 걸리지 않는다 — 챌린지는 지출 카테고리뿐이다")
        void incomeDoesNotTouchAnyChallenge() throws Exception {
            Challenge challenge = givenDeliveryChallenge();

            createTransaction(SALARY, 3_000_000, TODAY);

            assertThat(reload(challenge).getSpendingAmount()).isZero();
        }

        @Test
        @DisplayName("다른 사용자의 챌린지는 움직이지 않는다 (COACHING.USER_ID 경유)")
        void scopedByUser() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "tx-challenge-other@email.com", "irrelevant-hash"));
            Challenge othersChallenge = givenChallenge(other, DELIVERY, TODAY, TODAY.plusDays(6));

            createTransaction(DELIVERY, 24_000, TODAY);

            assertThat(reload(othersChallenge).getSpendingAmount()).isZero();
        }

        @Test
        @DisplayName("종료된 챌린지는 거래가 바뀌어도 재계산되지 않는다 (서버_내부_처리 1장)")
        void finishedChallengesStayFrozen() throws Exception {
            Challenge challenge = givenDeliveryChallenge();
            challenge.updatePerformance(10_000, 1);
            challenge.finish();

            createTransaction(DELIVERY, 24_000, TODAY);

            // 종료 시점에 확정된 결과가 그대로 남는다 — 예외도 나지 않는다
            assertThat(reload(challenge)).satisfies(frozen -> {
                assertThat(frozen.getSpendingAmount()).isEqualTo(10_000);
                assertThat(frozen.getStatus()).isEqualTo(ChallengeStatus.SUCCESS);
            });
        }

        @Test
        @DisplayName("종료일 당일 거래도 실적이다 — 기간은 양끝을 포함한다")
        void includesTheLastDay() throws Exception {
            // 미래 날짜로는 거래를 만들 수 없으므로(@NotFutureDate) 오늘이 마지막 날인
            // 챌린지를 만든다 — 6일 전에 확정한 7일 챌린지다.
            Challenge challenge = givenChallenge(user, DELIVERY, TODAY.minusDays(6), TODAY);

            createTransaction(DELIVERY, 24_000, TODAY);

            assertThat(reload(challenge).getSpendingAmount()).isEqualTo(24_000);
        }

        @Test
        @DisplayName("시작일 당일 거래도 실적이다 — 반대쪽 끝도 포함이다")
        void includesTheFirstDay() throws Exception {
            Challenge challenge = givenDeliveryChallenge();

            createTransaction(DELIVERY, 24_000, TODAY);

            assertThat(reload(challenge).getSpendingAmount()).isEqualTo(24_000);
        }
    }

    // --- TX-05 수정 ---------------------------------------------------------

    @Nested
    @DisplayName("TX-05 수정")
    class Update {

        private void patchTransaction(long id, String body) throws Exception {
            mockMvc.perform(authed(patch("/transactions/{id}", id))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("금액을 고치면 실적이 따라 바뀐다")
        void amountChangeIsReflected() throws Exception {
            Challenge challenge = givenDeliveryChallenge();
            long transactionId = createTransaction(DELIVERY, 24_000, TODAY);

            patchTransaction(transactionId, """
                    {"amount": 40000}
                    """);

            assertThat(reload(challenge).getSpendingAmount()).isEqualTo(40_000);
        }

        @Test
        @DisplayName("날짜를 기간 밖으로 옮기면 실적에서 빠진다 — 수정 전 날짜로도 재계산해야 한다")
        void movingTheDateOutOfThePeriodRemovesIt() throws Exception {
            Challenge challenge = givenDeliveryChallenge();
            long transactionId = createTransaction(DELIVERY, 24_000, TODAY);
            assertThat(reload(challenge).getSpendingAmount()).isEqualTo(24_000);

            // 수정 후 날짜만 재계산하면 걸리는 챌린지가 없어 24,000원이 그대로 남는다
            patchTransaction(transactionId, """
                    {"transactionDate": "%s"}
                    """.formatted(TODAY.minusDays(3)));

            assertThat(reload(challenge)).satisfies(updated -> {
                assertThat(updated.getSpendingAmount()).isZero();
                assertThat(updated.getSpendingCount()).isZero();
            });
        }

        @Test
        @DisplayName("날짜를 기간 안으로 옮기면 실적에 들어온다")
        void movingTheDateIntoThePeriodAddsIt() throws Exception {
            Challenge challenge = givenDeliveryChallenge();
            long transactionId = createTransaction(DELIVERY, 24_000, TODAY.minusDays(3));
            assertThat(reload(challenge).getSpendingAmount()).isZero();

            patchTransaction(transactionId, """
                    {"transactionDate": "%s"}
                    """.formatted(TODAY));

            assertThat(reload(challenge).getSpendingAmount()).isEqualTo(24_000);
        }

        @Test
        @DisplayName("카테고리를 바꾸면 이전·새 카테고리의 챌린지가 모두 갱신된다")
        void categoryChangeUpdatesBothChallenges() throws Exception {
            Challenge delivery = givenDeliveryChallenge();
            Challenge food = givenChallenge(user, FOOD, TODAY, TODAY.plusDays(6));
            long transactionId = createTransaction(DELIVERY, 24_000, TODAY);

            patchTransaction(transactionId, """
                    {"categoryId": %d}
                    """.formatted(FOOD));

            assertThat(reload(delivery).getSpendingAmount()).isZero();
            assertThat(reload(food).getSpendingAmount()).isEqualTo(24_000);
        }
    }

    // --- TX-06 삭제 ---------------------------------------------------------

    @Test
    @DisplayName("TX-06: 거래를 지우면 실적이 줄어든다 — 재집계가 누적이 아닌 덮어쓰기여야 성립한다")
    void deleteUpdatesThePerformance() throws Exception {
        Challenge challenge = givenDeliveryChallenge();
        long first = createTransaction(DELIVERY, 24_000, TODAY);
        createTransaction(DELIVERY, 11_000, TODAY);
        assertThat(reload(challenge).getSpendingAmount()).isEqualTo(35_000);

        mockMvc.perform(authed(delete("/transactions/{id}", first)))
                .andExpect(status().isNoContent());

        assertThat(reload(challenge)).satisfies(updated -> {
            assertThat(updated.getSpendingAmount()).isEqualTo(11_000);
            assertThat(updated.getSpendingCount()).isEqualTo(1);
            assertThat(updated.getAchievementRate()).isEqualByComparingTo("22.00");
        });
    }

    @Test
    @DisplayName("마지막 거래를 지우면 0 으로 돌아간다")
    void deletingEverythingResetsThePerformance() throws Exception {
        Challenge challenge = givenDeliveryChallenge();
        long only = createTransaction(DELIVERY, 24_000, TODAY);

        mockMvc.perform(authed(delete("/transactions/{id}", only)))
                .andExpect(status().isNoContent());

        assertThat(reload(challenge)).satisfies(updated -> {
            assertThat(updated.getSpendingAmount()).isZero();
            assertThat(updated.getSpendingCount()).isZero();
            assertThat(updated.getAchievementRate()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    @DisplayName("챌린지가 없어도 거래 등록·수정·삭제는 그대로 된다")
    void worksWithoutAnyChallenge() throws Exception {
        long transactionId = createTransaction(DELIVERY, 24_000, TODAY);

        mockMvc.perform(authed(patch("/transactions/{id}", transactionId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 30000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(30_000));
        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        assertThat(transactionRepository.findById(transactionId)).isEmpty();
    }
}
