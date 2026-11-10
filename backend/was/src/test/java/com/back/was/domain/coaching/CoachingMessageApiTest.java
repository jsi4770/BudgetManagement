package com.back.was.domain.coaching;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACH-03 메시지 전송.
 *
 * <p><b>지금은 모든 응답이 대체 응답이다.</b> AI 어댑터가 없어
 * {@code UnavailableCoachingReplyGenerator} 가 항상 던지고, 서비스가 그것을 잡아
 * {@code docs/AI_대체_문구.md} 4·5장으로 200 을 만든다(서버_내부_처리 8장). 그래서 여기의
 * 기대값은 거의 전부 {@code isFallback: true} 쪽이고, <b>그게 의도된 현재 동작</b>이다 —
 * 503 이 나가면 명세 위반이다.
 *
 * <p>제안 숫자는 규칙 기반 제안(5장)의 결과다. 계산 자체는
 * {@code RuleBasedGoalProposerTest} 가 검증하고, 여기서는 그것이 <b>응답에 실리고 DB 에
 * 저장되는지</b>를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachingMessageApiTest {

    private static final String EMAIL = "coaching-message@email.com";
    private static final String PASSWORD = "password123!";

    private static final long DELIVERY = 2L;

    private static final DateRange LAST_FOUR_WEEKS = Weeks.fullWeeksBefore(KstTime.today(), 4);

    /** AI_대체_문구 4장 1·2번 — 제안 없이 보낼 때. */
    private static final String[] MESSAGES_WITHOUT_PROPOSAL = {
            "지금은 AI 코치가 잠시 쉬고 있어요. 잠시 후에 다시 말을 걸어 주세요.",
            "답변을 준비하는 데 문제가 생겼어요. 조금 뒤에 다시 시도해 주세요."
    };

    /** AI_대체_문구 4장 3번 — 규칙 기반 제안과 함께 보낼 때만. */
    private static final String MESSAGE_WITH_PROPOSAL =
            "지금은 대화가 원활하지 않아요. 아래 추천 목표로 바로 시작해 볼 수도 있어요.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CoachingMessageRepository coachingMessageRepository;

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

    private MockHttpServletRequestBuilder sendRequest(Object coachingId, String body) {
        return authed(post("/coachings/{id}/messages", coachingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Coaching givenCoaching(User owner) {
        Coaching coaching = coachingRepository.save(Coaching.start(owner));
        coachingMessageRepository.save(CoachingMessage.ai(coaching, "안녕하세요! 어떤 점이 신경 쓰이세요?"));
        return coaching;
    }

    /** 규칙 기반 제안이 배달 40,000원을 고르게 만든다 (직전 4주 합계 240,000 → 주간 평균 60,000). */
    private void givenDeliverySpending() {
        Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();
        transactionRepository.save(Transaction.of(
                user, delivery, 240_000, LAST_FOUR_WEEKS.start(), null));
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 COACH-03: 200 + 사용자 메시지와 AI 메시지를 함께 준다")
    void returnsBothMessages() throws Exception {
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "퇴근 후 배달이 잦아요"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userMessage.id").isNumber())
                .andExpect(jsonPath("$.data.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.data.userMessage.content").value("퇴근 후 배달이 잦아요"))
                .andExpect(jsonPath("$.data.userMessage.createdAt").value(Matchers.containsString("+09:00")))
                // 사용자 메시지에는 isFallback 이 없다 — aiMessage 전용 키다
                .andExpect(jsonPath("$.data.userMessage.isFallback").doesNotExist())
                .andExpect(jsonPath("$.data.aiMessage.id").isNumber())
                .andExpect(jsonPath("$.data.aiMessage.role").value("AI"))
                .andExpect(jsonPath("$.data.aiMessage.content").value(Matchers.not(Matchers.emptyString())))
                .andExpect(jsonPath("$.data.aiMessage.createdAt").value(Matchers.containsString("+09:00")));
    }

    @Test
    @DisplayName("AI 어댑터가 없으면 503 이 아니라 200 + isFallback: true 다 (서버_내부_처리 8장)")
    void aiFailureStillAnswersTwoHundred() throws Exception {
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "도와주세요"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMessage.isFallback").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("제안을 만들 수 있으면 4장 3번 문구 + proposal 을 함께 준다")
    void fallbackCarriesTheRuleBasedProposal() throws Exception {
        Coaching coaching = givenCoaching(user);
        givenDeliverySpending();

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "배달을 줄이고 싶어요"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMessage.isFallback").value(true))
                .andExpect(jsonPath("$.data.aiMessage.content").value(MESSAGE_WITH_PROPOSAL))
                .andExpect(jsonPath("$.data.proposal.category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data.proposal.category.name").value("배달"))
                .andExpect(jsonPath("$.data.proposal.goalType").value("AMOUNT"))
                .andExpect(jsonPath("$.data.proposal.goalAmount").value(40_000))
                .andExpect(jsonPath("$.data.proposal.goalCount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.proposal.periodDays").value(7))
                .andExpect(jsonPath("$.data.proposal.title").value("배달 40,000원 이내"));
    }

    @Test
    @DisplayName("제안을 만들 수 없으면 proposal 은 null 이고 4장 1·2번 문구만 간다")
    void fallbackWithoutProposalUsesTheOtherTemplates() throws Exception {
        // 가입 직후 — 넛지도, 소비 관리 목표도, 직전 4주 지출도 없다
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "안녕하세요"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal").value(Matchers.nullValue()))
                // "아래 추천 목표로" 문구는 가리킬 카드가 있을 때만 쓴다 (4장 3번 조건)
                .andExpect(jsonPath("$.data.aiMessage.content").value(Matchers.oneOf(MESSAGES_WITHOUT_PROPOSAL)));
    }

    // --- 저장 (서버_내부_처리 6장) -------------------------------------------

    @Test
    @DisplayName("두 메시지가 순서대로 저장된다 — COACH-02 로 다시 열면 대화가 이어져 있다")
    void persistsBothMessagesInOrder() throws Exception {
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "퇴근 후 배달이 잦아요"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages", Matchers.hasSize(3)))
                .andExpect(jsonPath("$.data.messages[0].role").value("AI"))
                .andExpect(jsonPath("$.data.messages[1].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[1].content").value("퇴근 후 배달이 잦아요"))
                .andExpect(jsonPath("$.data.messages[2].role").value("AI"));
    }

    @Test
    @DisplayName("제안은 PROPOSAL_ 컬럼에 저장된다 — COACH-02 가 제안 카드를 복원한다")
    void persistsTheProposal() throws Exception {
        Coaching coaching = givenCoaching(user);
        givenDeliverySpending();

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "배달을 줄이고 싶어요"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal.goalAmount").value(40_000))
                .andExpect(jsonPath("$.data.proposal.title").value("배달 40,000원 이내"));
    }

    @Test
    @DisplayName("isFallback 은 저장되지 않는다 (ERD 8장) — COACH-02 의 메시지에는 그 키가 없다")
    void fallbackFlagIsNotPersisted() throws Exception {
        Coaching coaching = givenCoaching(user);

        mockMvc.perform(sendRequest(coaching.getId(), """
                        {"content": "안녕하세요"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMessage.isFallback").value(true));

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[2].isFallback").doesNotExist());
    }

    @Test
    @DisplayName("메시지를 다시 보내면 제안도 다시 온다 (명세 COACH-03 '재제안이 필요하면 메시지 재전송')")
    void resendingAMessageProposesAgain() throws Exception {
        Coaching coaching = givenCoaching(user);
        givenDeliverySpending();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(sendRequest(coaching.getId(), """
                            {"content": "다시 제안해 주세요"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.proposal.goalAmount").value(40_000));
        }

        // 제안은 최신 1개만 보관한다 — 행이 늘지 않고 덮어써진다 (ERD 7장)
        assertThat(coachingRepository.findById(coaching.getId()).orElseThrow().hasProposal()).isTrue();
        assertThat(coachingMessageRepository.findByCoachingIdOrderByCreatedAtAscIdAsc(coaching.getId()))
                .hasSize(5);
    }

    // --- 검증·접근 범위 ------------------------------------------------------

    @Nested
    @DisplayName("요청 검증 (명세 COACH-03: content 1~500자 필수)")
    class Validation {

        @Test
        @DisplayName("content 가 없거나 비어 있거나 공백뿐이면 400 REQUIRED")
        void rejectsBlankContent() throws Exception {
            Coaching coaching = givenCoaching(user);

            for (String body : new String[]{"{}", """
                    {"content": ""}
                    """, """
                    {"content": "   "}
                    """}) {
                mockMvc.perform(sendRequest(coaching.getId(), body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                        .andExpect(jsonPath("$.error.details[0].field").value("content"))
                        .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
            }
        }

        @Test
        @DisplayName("500자는 되고 501자는 400 — 상한은 자르지 않고 거부한다")
        void enforcesTheLengthCap() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(sendRequest(coaching.getId(), """
                            {"content": "%s"}
                            """.formatted("가".repeat(500))))
                    .andExpect(status().isOk());

            mockMvc.perform(sendRequest(coaching.getId(), """
                            {"content": "%s"}
                            """.formatted("가".repeat(501))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("content"));
        }

        @Test
        @DisplayName("검증에 걸린 요청은 메시지를 남기지 않는다")
        void savesNothingWhenRejected() throws Exception {
            Coaching coaching = givenCoaching(user);
            long before = coachingMessageRepository.count();

            mockMvc.perform(sendRequest(coaching.getId(), "{}"))
                    .andExpect(status().isBadRequest());

            assertThat(coachingMessageRepository.count()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("접근 범위")
    class Access {

        @Test
        @DisplayName("없는 코칭과 남의 코칭은 똑같이 404 다 (명세 0장 5))")
        void unknownOrOthersCoachingIsNotFound() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "coaching-message-other@email.com", "irrelevant-hash"));
            Coaching othersCoaching = givenCoaching(other);

            String missing = mockMvc.perform(sendRequest(999_999, """
                            {"content": "안녕하세요"}
                            """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();

            String others = mockMvc.perform(sendRequest(othersCoaching.getId(), """
                            {"content": "안녕하세요"}
                            """))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            assertThat(others).isEqualTo(missing);
        }

        @Test
        @DisplayName("종료된 코칭이면 409 COACHING_ALREADY_ENDED (명세 COACH-03 Errors)")
        void endedCoachingIsRejected() throws Exception {
            Coaching coaching = givenCoaching(user);
            coaching.cancel();

            mockMvc.perform(sendRequest(coaching.getId(), """
                            {"content": "안녕하세요"}
                            """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COACHING_ALREADY_ENDED"));
        }

        @Test
        @DisplayName("409 면 메시지를 남기지 않는다 — 읽을 수만 있는 대화에 줄이 붙지 않는다")
        void savesNothingWhenEnded() throws Exception {
            Coaching coaching = givenCoaching(user);
            coaching.cancel();
            long before = coachingMessageRepository.count();

            mockMvc.perform(sendRequest(coaching.getId(), """
                            {"content": "안녕하세요"}
                            """))
                    .andExpect(status().isConflict());

            assertThat(coachingMessageRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(post("/coachings/{id}/messages", coaching.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"content": "안녕하세요"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
