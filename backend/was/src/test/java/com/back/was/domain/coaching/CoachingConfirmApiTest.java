package com.back.was.domain.coaching;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACH-04 목표 확정 → 챌린지 생성.
 *
 * <p>명세 COACH-04 의 예시를 기준값으로 쓴다: 배달 / 50,000원 / 7일 / 11-04 ~ 11-10.
 * 날짜는 오늘(KST)로 옮긴다 — {@code startDate} 가 "오늘 이후"여야 하므로 고정 날짜를 쓰면
 * 테스트가 날이 바뀔 때 깨진다.
 *
 * <p>여기서 가장 중요한 두 가지는 <b>스냅샷</b>(요청 값이 제안과 달라도 되고, 챌린지에는
 * 요청 값이 남는다 — ERD 9장)과 <b>진행 중 챌린지 1개</b>(ERD 9장 등록 규칙)다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachingConfirmApiTest {

    private static final String EMAIL = "coaching-confirm@email.com";
    private static final String PASSWORD = "password123!";

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    /** 수입 카테고리 — ERD 4장 시드의 급여. */
    private static final long SALARY = 8L;

    private static final LocalDate TODAY = KstTime.today();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CoachingMessageRepository coachingMessageRepository;

    @Autowired
    ChallengeRepository challengeRepository;

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

    private MockHttpServletRequestBuilder confirmRequest(Object coachingId, String body) {
        return authed(post("/coachings/{id}/confirm", coachingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    /** 명세 COACH-04 예시의 본문. 7일 챌린지 = 오늘 ~ 오늘+6. */
    private static String amountGoalBody() {
        return """
                {"categoryId": %d, "title": "배달비 50,000원 이내", "goalType": "AMOUNT",
                 "goalAmount": 50000, "goalCount": null,
                 "startDate": "%s", "endDate": "%s"}
                """.formatted(DELIVERY, TODAY, TODAY.plusDays(6));
    }

    private Coaching givenCoaching(User owner) {
        Coaching coaching = coachingRepository.save(Coaching.start(owner));
        coachingMessageRepository.save(CoachingMessage.ai(coaching, "어떤 목표로 시작해 볼까요?"));
        return coaching;
    }

    /** 제안이 저장된 코칭 — 확정 값이 제안과 달라도 되는지 보려면 제안이 있어야 한다. */
    private Coaching givenCoachingWithProposal(User owner) {
        Coaching coaching = givenCoaching(owner);
        Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();
        coaching.propose(delivery, "배달 40,000원 이내", GoalType.AMOUNT, 40_000, null, 7);
        return coaching;
    }

    /** 다른(이미 끝난) 코칭에 걸린 진행 중 챌린지. */
    private Challenge givenRunningChallenge(User owner) {
        Coaching previous = coachingRepository.save(Coaching.start(owner));
        previous.cancel();
        Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();

        return challengeRepository.save(Challenge.of(
                previous, delivery, "진행 중 챌린지", GoalType.AMOUNT, 30_000, null,
                TODAY, TODAY.plusDays(6)));
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 COACH-04: 201 + challengeId · 종료된 코칭 · 확정 시각")
    void confirmsAndCreatesAChallenge() throws Exception {
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.challengeId").isNumber())
                .andExpect(jsonPath("$.data.coaching.id").value(coaching.getId()))
                .andExpect(jsonPath("$.data.coaching.status").value("COMPLETED"))
                // 챌린지 createdAt = 코칭 종료 시각 (ERD 7장)
                .andExpect(jsonPath("$.data.challengeCreatedAt").value(Matchers.containsString("+09:00")));
    }

    @Test
    @DisplayName("코칭이 COMPLETED 로 끝난다 — 확정은 대화의 종료다")
    void completesTheCoaching() throws Exception {
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                .andExpect(status().isCreated());

        assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.COMPLETED);
    }

    @Test
    @DisplayName("확정 값이 그대로 챌린지에 남는다 (ERD 9장 스냅샷) — 실적은 0 에서 시작")
    void storesTheRequestedValues() throws Exception {
        Coaching coaching = givenCoaching(user);

        String body = mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long challengeId = ((Number) JsonPath.read(body, "$.data.challengeId")).longValue();

        assertThat(challengeRepository.findById(challengeId).orElseThrow()).satisfies(challenge -> {
            assertThat(challenge.getCoaching().getId()).isEqualTo(coaching.getId());
            assertThat(challenge.getCategory().getId()).isEqualTo(DELIVERY);
            assertThat(challenge.getTitle()).isEqualTo("배달비 50,000원 이내");
            assertThat(challenge.getGoalType()).isEqualTo(GoalType.AMOUNT);
            assertThat(challenge.getGoalAmount()).isEqualTo(50_000);
            assertThat(challenge.getGoalCount()).isNull();
            assertThat(challenge.getStartDate()).isEqualTo(TODAY);
            assertThat(challenge.getEndDate()).isEqualTo(TODAY.plusDays(6));
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
            // 실적은 후처리 2번이 채운다 — 확정 직후에는 0 이다
            assertThat(challenge.getSpendingAmount()).isZero();
            assertThat(challenge.getSpendingCount()).isZero();
            assertThat(challenge.getAchievementRate()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    @DisplayName("확정 값은 저장된 제안과 달라도 된다 (± 조정) — 제안은 기록으로 남는다")
    void acceptsValuesDifferentFromTheProposal() throws Exception {
        Coaching coaching = givenCoachingWithProposal(user);   // 제안: 배달 40,000원 / 7일

        // 사용자가 [+] 로 50,000 으로 올려서 확정
        String body = mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long challengeId = ((Number) JsonPath.read(body, "$.data.challengeId")).longValue();

        // 확정 이후의 기준은 챌린지 값 (ERD 7장)
        assertThat(challengeRepository.findById(challengeId).orElseThrow().getGoalAmount())
                .isEqualTo(50_000);
        // 제안 값은 바뀌지 않는다 — "AI 제안을 그대로 확정한 비율" 같은 분석용 기록이다
        assertThat(coaching.getProposalGoalAmount()).isEqualTo(40_000);
    }

    @Test
    @DisplayName("COUNT 목표도 확정된다 — 금액 쪽 컬럼은 비워진다")
    void confirmsACountGoal() throws Exception {
        Coaching coaching = givenCoaching(user);

        String body = mockMvc.perform(confirmRequest(coaching.getId(), """
                        {"categoryId": %d, "title": "배달 2회 이하", "goalType": "COUNT",
                         "goalAmount": null, "goalCount": 2,
                         "startDate": "%s", "endDate": "%s"}
                        """.formatted(DELIVERY, TODAY, TODAY.plusDays(13))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long challengeId = ((Number) JsonPath.read(body, "$.data.challengeId")).longValue();

        assertThat(challengeRepository.findById(challengeId).orElseThrow()).satisfies(challenge -> {
            assertThat(challenge.getGoalType()).isEqualTo(GoalType.COUNT);
            assertThat(challenge.getGoalCount()).isEqualTo(2);
            assertThat(challenge.getGoalAmount()).isNull();
        });
    }

    @Test
    @DisplayName("확정한 코칭도 COACH-02 로 열린다 — 대화와 제안이 그대로 복원된다")
    void theCompletedCoachingIsStillReadable() throws Exception {
        Coaching coaching = givenCoachingWithProposal(user);

        mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.proposal.goalAmount").value(40_000));
    }

    // --- 진행 중 챌린지 1개 (ERD 9장 등록 규칙) ------------------------------

    @Nested
    @DisplayName("진행 중 챌린지 1개")
    class OneChallengeAtATime {

        @Test
        @DisplayName("이미 진행 중 챌린지가 있으면 409 CHALLENGE_ALREADY_IN_PROGRESS")
        void rejectsASecondRunningChallenge() throws Exception {
            givenRunningChallenge(user);
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CHALLENGE_ALREADY_IN_PROGRESS"));
        }

        @Test
        @DisplayName("409 면 챌린지도 생기지 않고 코칭도 살아 있다 — 다시 확정할 수 있다")
        void changesNothingWhenRejected() throws Exception {
            givenRunningChallenge(user);
            Coaching coaching = givenCoaching(user);
            long before = challengeRepository.count();

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isConflict());

            assertThat(challengeRepository.count()).isEqualTo(before);
            assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("종료된 챌린지는 막지 않는다 — 끝낸 사용자는 다음 목표를 세울 수 있다")
        void finishedChallengesDoNotBlock() throws Exception {
            givenRunningChallenge(user).finish();
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("다른 사용자의 진행 중 챌린지는 막지 않는다 (COACHING.USER_ID 경유)")
        void scopedByUser() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "confirm-other@email.com", "irrelevant-hash"));
            givenRunningChallenge(other);
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("같은 코칭을 두 번 확정하면 409 COACHING_ALREADY_ENDED — 챌린지는 하나뿐이다")
        void doubleConfirmIsRejected() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isCreated());

            // 두 조건이 동시에 참인 유일한 경우다 — 경로가 지목한 리소스 쪽을 먼저 본다
            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COACHING_ALREADY_ENDED"));

            assertThat(challengeRepository.count()).isEqualTo(1);
        }
    }

    // --- 요청 검증 (명세 COACH-04 표) ---------------------------------------

    @Nested
    @DisplayName("요청 검증")
    class Validation {

        @Test
        @DisplayName("필수 필드가 빠지면 400 REQUIRED")
        void rejectsMissingFields() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[*].field",
                            Matchers.hasItems("categoryId", "title", "goalType", "startDate", "endDate")))
                    .andExpect(jsonPath("$.error.details[*].reason",
                            Matchers.everyItem(Matchers.is("REQUIRED"))));
        }

        @Test
        @DisplayName("startDate 가 어제면 400 PAST_DATE — 지난 날짜로 챌린지를 시작할 수 없다")
        void rejectsAPastStartDate() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "배달 50,000원 이내", "goalType": "AMOUNT",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, TODAY.minusDays(1), TODAY.plusDays(5))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("startDate"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("PAST_DATE"));
        }

        @Test
        @DisplayName("오늘 시작은 허용한다 — 명세 예시가 확정 당일을 startDate 로 쓴다")
        void allowsStartingToday() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("기간이 7·14·30일이 아니면 400 — 10일짜리 챌린지는 없다")
        void rejectsAnUnlistedPeriod() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "배달 50,000원 이내", "goalType": "AMOUNT",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, TODAY, TODAY.plusDays(9))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("7·14·30일은 모두 허용한다 (명세 COACH-04 '기간은 7·14·30일 중 선택')")
        void allowsTheThreePeriods() throws Exception {
            for (int days : new int[]{7, 14, 30}) {
                Coaching coaching = givenCoaching(user);

                mockMvc.perform(confirmRequest(coaching.getId(), """
                                {"categoryId": %d, "title": "배달 목표", "goalType": "AMOUNT",
                                 "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                                """.formatted(DELIVERY, TODAY, TODAY.plusDays(days - 1))))
                        .andExpect(status().isCreated());

                // 다음 반복을 위해 진행 중 챌린지를 치운다 (사용자당 1개)
                challengeRepository.findAll().forEach(challenge -> {
                    if (!challenge.isFinished()) {
                        challenge.finish();
                    }
                });
            }
        }

        @Test
        @DisplayName("endDate 가 startDate 보다 앞이면 400 — 기간 계산이 0 이하가 된다")
        void rejectsAReversedPeriod() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "배달 목표", "goalType": "AMOUNT",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, TODAY.plusDays(6), TODAY)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("목표 유형에 맞는 목표 값이 없으면 400 (ERD 9장 체크 조건)")
        void rejectsAGoalThatDoesNotMatchTheType() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "배달 목표", "goalType": "AMOUNT",
                             "goalCount": 2, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, TODAY, TODAY.plusDays(6))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("목표 값이 0 이하면 400 (명세: 1 이상)")
        void rejectsANonPositiveGoal() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "배달 목표", "goalType": "AMOUNT",
                             "goalAmount": 0, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, TODAY, TODAY.plusDays(6))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[0].field").value("goalAmount"));
        }

        @Test
        @DisplayName("goalType 에 없는 값을 보내면 400 — 열거형 밖은 역직렬화에서 걸린다")
        void rejectsAnUnknownGoalType() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "배달 목표", "goalType": "FOO",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, TODAY, TODAY.plusDays(6))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("title 이 100자를 넘으면 400")
        void rejectsALongTitle() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "%s", "goalType": "AMOUNT",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(DELIVERY, "가".repeat(101), TODAY, TODAY.plusDays(6))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[0].field").value("title"));
        }

        @Test
        @DisplayName("없는 카테고리는 404, 수입 카테고리는 400 — 절약 목표는 지출에만 걸린다")
        void rejectsCategoriesThatCannotCarryAGoal() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": 999999, "title": "배달 목표", "goalType": "AMOUNT",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(TODAY, TODAY.plusDays(6))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

            mockMvc.perform(confirmRequest(coaching.getId(), """
                            {"categoryId": %d, "title": "급여 목표", "goalType": "AMOUNT",
                             "goalAmount": 50000, "startDate": "%s", "endDate": "%s"}
                            """.formatted(SALARY, TODAY, TODAY.plusDays(6))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("검증에 걸린 요청은 챌린지를 만들지 않고 코칭도 끝내지 않는다")
        void changesNothingWhenRejected() throws Exception {
            Coaching coaching = givenCoaching(user);
            long before = challengeRepository.count();

            mockMvc.perform(confirmRequest(coaching.getId(), "{}"))
                    .andExpect(status().isBadRequest());

            assertThat(challengeRepository.count()).isEqualTo(before);
            assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("확정하지 않은 제안은 챌린지가 되지 않는다 (SRS 7.3-3)")
        void anUnconfirmedProposalNeverBecomesAChallenge() throws Exception {
            givenCoachingWithProposal(user);

            // 제안만 저장된 상태로는 챌린지가 없다 — 만드는 경로가 확정뿐이다
            assertThat(challengeRepository.count()).isZero();
        }
    }

    // --- 접근 범위 ----------------------------------------------------------

    @Nested
    @DisplayName("접근 범위")
    class Access {

        @Test
        @DisplayName("없는 코칭과 남의 코칭은 똑같이 404 다 (명세 0장 5))")
        void unknownOrOthersCoachingIsNotFound() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "confirm-other2@email.com", "irrelevant-hash"));
            Coaching othersCoaching = givenCoaching(other);

            String missing = mockMvc.perform(confirmRequest(999_999, amountGoalBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();

            String others = mockMvc.perform(confirmRequest(othersCoaching.getId(), amountGoalBody()))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            assertThat(others).isEqualTo(missing);
        }

        @Test
        @DisplayName("취소된 코칭이면 409 COACHING_ALREADY_ENDED")
        void canceledCoachingIsRejected() throws Exception {
            Coaching coaching = givenCoaching(user);
            coaching.cancel();

            mockMvc.perform(confirmRequest(coaching.getId(), amountGoalBody()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COACHING_ALREADY_ENDED"));
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(post("/coachings/{id}/confirm", coaching.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(amountGoalBody()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    @DisplayName("다른 카테고리로도 확정된다 — 카테고리는 요청이 정한다")
    void confirmsWithAnyExpenseCategory() throws Exception {
        Coaching coaching = givenCoaching(user);

        String body = mockMvc.perform(confirmRequest(coaching.getId(), """
                        {"categoryId": %d, "title": "식비 100,000원 이내", "goalType": "AMOUNT",
                         "goalAmount": 100000, "startDate": "%s", "endDate": "%s"}
                        """.formatted(FOOD, TODAY, TODAY.plusDays(29))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long challengeId = ((Number) JsonPath.read(body, "$.data.challengeId")).longValue();

        assertThat(challengeRepository.findById(challengeId).orElseThrow().getCategory().getId())
                .isEqualTo(FOOD);
    }
}
