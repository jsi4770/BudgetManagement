package com.back.was.domain.coaching;

import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.jayway.jsonpath.JsonPath;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACH-01 코칭 시작.
 *
 * <p>첫 메시지는 Gemini 가 없으므로 {@code TemplateCoachingOpeningGenerator} 가 만든다 —
 * 문구 자체는 {@code TemplateCoachingOpeningGeneratorTest} 가 검증하고, 여기서는 <b>어느
 * 갈래의 문구가 쓰였는지</b>(넛지 맥락 포함 여부)만 본다. 넛지 숫자는 docs/시연_데이터.md
 * 6-1 의 배달 판정(101,000 vs 평균 60,000)과 같은 값이 되도록
 * {@link SpendingComparison} 을 거쳐 만든다.
 *
 * <p><b>진행 중 챌린지는 대화 시작을 막지 않는다</b>(명세 COACH-01 · ERD 7장: "확정만
 * 차단"). 2026-11-09 에 한 번 409 로 막아 봤다가 명세 위반이라 되돌렸으므로,
 * {@code doesNotBlockWhileAChallengeRuns} 가 그 규칙을 고정한다 — 같은 실수를 다시 하면
 * 여기서 걸린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachingStartApiTest {

    private static final String EMAIL = "coaching@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final DateRange THIS_WEEK = Weeks.elapsedWeekOf(TODAY);

    private static final long DELIVERY = 2L;

    /** 시연 6-1 의 배달 — 금액 101,000 · 평균 60,000 · 차이 41,000. */
    private static final SpendingComparison DELIVERY_SPIKE =
            SpendingComparison.of(101_000, List.of(55_000, 62_000, 58_000, 65_000));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CoachingMessageRepository coachingMessageRepository;

    @Autowired
    NudgeRepository nudgeRepository;

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

    /** 본문을 지정해서 호출한다. {@code body} 가 {@code null} 이면 본문 없이 보낸다. */
    private MockHttpServletRequestBuilder startRequest(String body) {
        MockHttpServletRequestBuilder request = authed(post("/coachings"));
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Nudge givenNudge(User owner) {
        Category category = categoryRepository.findById(DELIVERY).orElseThrow();
        return nudgeRepository.save(Nudge.of(owner, category, THIS_WEEK, DELIVERY_SPIKE));
    }

    private Coaching givenInProgressCoaching(User owner) {
        return coachingRepository.save(Coaching.start(owner));
    }

    /**
     * 확정된 챌린지 하나. 사용자는 코칭을 거쳐서만 알 수 있으므로(ERD 9장)
     * 코칭을 먼저 만들어 붙인다 — 그 코칭이 {@code CANCELED} 인 것은 "이전 대화에서
     * 확정하고 그 세션은 끝난" 정상 상태다.
     */
    private Challenge givenChallenge(User owner) {
        Coaching coaching = givenInProgressCoaching(owner);
        coaching.cancel();
        Category category = categoryRepository.findById(DELIVERY).orElseThrow();

        return challengeRepository.save(Challenge.of(
                coaching, category, "배달 50,000원 이내", GoalType.AMOUNT, 50_000, null,
                TODAY, TODAY.plusDays(6)));
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 COACH-01: 201 + 세션과 AI 첫 메시지 한 개. proposal 은 null 키로 남는다")
    void startsCoachingWithAnOpeningMessage() throws Exception {
        mockMvc.perform(startRequest("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.coachingId").isNumber())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.createdAt").value(Matchers.containsString("+09:00")))
                // 제안 전에는 null 이다 — 키가 사라지면 프론트가 "제안 카드 없음"을
                // 판별할 수 없다 (명세 COACH-01 응답 예시).
                .andExpect(jsonPath("$.data.proposal").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.messages", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.messages[0].id").isNumber())
                .andExpect(jsonPath("$.data.messages[0].role").value("AI"))
                .andExpect(jsonPath("$.data.messages[0].content").value(Matchers.not(Matchers.emptyString())))
                .andExpect(jsonPath("$.data.messages[0].createdAt").value(Matchers.containsString("+09:00")))
                // isFallback 은 COACH-03 의 aiMessage 전용이다 (ERD 8장: 저장하지 않음)
                .andExpect(jsonPath("$.data.messages[0].isFallback").doesNotExist());
    }

    @Test
    @DisplayName("본문 없이 POST 해도 201 — nudgeId 가 선택이라 보낼 값이 없는 요청이 정상이다")
    void acceptsAnEmptyBody() throws Exception {
        mockMvc.perform(startRequest(null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("첫 메시지는 DB 에도 AI 역할로 남는다 — COACH-02 재진입 복원이 이걸 읽는다")
    void persistsTheOpeningMessage() throws Exception {
        String body = mockMvc.perform(startRequest("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long coachingId = ((Number) JsonPath.read(body, "$.data.coachingId")).longValue();
        String content = JsonPath.read(body, "$.data.messages[0].content");

        assertThat(coachingMessageRepository.findByCoachingIdOrderByCreatedAtAscIdAsc(coachingId))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getRole()).isEqualTo(MessageRole.AI);
                    assertThat(message.getContent()).isEqualTo(content);
                });
    }

    // --- 넛지에서 시작한 경우 (명세 COACH-01 nudgeId) -----------------------

    @Test
    @DisplayName("nudgeId 를 보내면 첫 메시지가 그 카드의 숫자를 인용한다 (AI_대체_문구 3장)")
    void quotesTheNudgeInTheOpeningMessage() throws Exception {
        Nudge nudge = givenNudge(user);

        mockMvc.perform(startRequest("""
                        {"nudgeId": %d}
                        """.formatted(nudge.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messages[0].content")
                        .value(Matchers.allOf(
                                Matchers.containsString("홍길동님"),
                                Matchers.containsString("배달"))));
    }

    @Test
    @DisplayName("nudgeId 가 없으면 숫자 없는 인사말이다 — 인용할 카드가 없다")
    void greetsWithoutANudge() throws Exception {
        givenNudge(user);

        mockMvc.perform(startRequest("{}"))
                .andExpect(status().isCreated())
                // 넛지가 DB 에 있어도 보내지 않았으면 쓰지 않는다 — 서버가 알아서 고르지
                // 않는다는 것이 명세의 전제다(맥락은 프론트가 지정한다).
                .andExpect(jsonPath("$.data.messages[0].content")
                        .value(Matchers.not(Matchers.containsString("배달"))));
    }

    @Test
    @DisplayName("nudgeId 를 보내면 그 넛지가 COACHING_STARTED 로 바뀐다 (명세 9장 상태 표)")
    void marksTheNudgeAsCoachingStarted() throws Exception {
        Nudge nudge = givenNudge(user);

        mockMvc.perform(startRequest("""
                        {"nudgeId": %d}
                        """.formatted(nudge.getId())))
                .andExpect(status().isCreated());

        assertThat(nudge.getStatus()).isEqualTo(NudgeStatus.COACHING_STARTED);
    }

    @Test
    @DisplayName("넛지는 코칭에 저장되지 않는다 — ERD 7장은 넛지와의 FK 를 두지 않았다")
    void doesNotStoreTheNudge() throws Exception {
        Nudge nudge = givenNudge(user);

        String body = mockMvc.perform(startRequest("""
                        {"nudgeId": %d}
                        """.formatted(nudge.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long coachingId = ((Number) JsonPath.read(body, "$.data.coachingId")).longValue();

        // 제안 카테고리는 AI 가 목표를 제안할 때 채워진다(COACH-03). 넛지의 카테고리가
        // 여기로 새어 들어오면 "제안하지 않은 제안"이 COACH-02 에서 카드로 뜬다.
        assertThat(coachingRepository.findById(coachingId).orElseThrow())
                .satisfies(coaching -> assertThat(coaching.hasProposal()).isFalse());
    }

    @Test
    @DisplayName("없는 nudgeId 와 남의 nudgeId 는 똑같이 404 다 (명세 0장 5))")
    void unknownOrOthersNudgeIsNotFound() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "coaching-other@email.com", "irrelevant-hash"));
        Nudge othersNudge = givenNudge(other);

        String missing = mockMvc.perform(startRequest("""
                        {"nudgeId": 999999}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        String others = mockMvc.perform(startRequest("""
                        {"nudgeId": %d}
                        """.formatted(othersNudge.getId())))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(others).isEqualTo(missing);
    }

    @Test
    @DisplayName("404 면 코칭이 생기지 않고 이전 코칭도 취소되지 않는다 — 넛지를 먼저 찾는 이유")
    void failedStartLeavesTheSessionsAlone() throws Exception {
        Coaching previous = givenInProgressCoaching(user);

        mockMvc.perform(startRequest("""
                        {"nudgeId": 999999}
                        """))
                .andExpect(status().isNotFound());

        assertThat(previous.getStatus()).isEqualTo(CoachingStatus.IN_PROGRESS);
        assertThat(coachingRepository.findAllByUserIdAndStatus(user.getId(), CoachingStatus.IN_PROGRESS))
                .containsExactly(previous);
    }

    // --- 이전 세션 취소 (명세 COACH-01 · ERD 7장) ---------------------------

    @Test
    @DisplayName("진행 중이던 코칭은 CANCELED 로 바뀌고 새 코칭이 IN_PROGRESS 가 된다")
    void cancelsThePreviousCoaching() throws Exception {
        Coaching previous = givenInProgressCoaching(user);

        String body = mockMvc.perform(startRequest("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long coachingId = ((Number) JsonPath.read(body, "$.data.coachingId")).longValue();

        assertThat(previous.getStatus()).isEqualTo(CoachingStatus.CANCELED);
        assertThat(coachingRepository.findAllByUserIdAndStatus(user.getId(), CoachingStatus.IN_PROGRESS))
                .singleElement()
                .satisfies(current -> assertThat(current.getId()).isEqualTo(coachingId));
    }

    @Test
    @DisplayName("세 번 시작해도 진행 중은 항상 하나다 — 이미 취소된 코칭을 다시 건드리지 않는다")
    void keepsExactlyOneInProgress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(startRequest("{}")).andExpect(status().isCreated());
        }

        assertThat(coachingRepository.findAllByUserIdAndStatus(user.getId(), CoachingStatus.IN_PROGRESS))
                .hasSize(1);
        assertThat(coachingRepository.findAllByUserIdAndStatus(user.getId(), CoachingStatus.CANCELED))
                .hasSize(2);
    }

    @Test
    @DisplayName("다른 사용자의 진행 중 코칭은 취소하지 않는다")
    void doesNotTouchOtherUsersCoaching() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "coaching-other2@email.com", "irrelevant-hash"));
        Coaching othersCoaching = givenInProgressCoaching(other);

        mockMvc.perform(startRequest("{}")).andExpect(status().isCreated());

        assertThat(othersCoaching.getStatus()).isEqualTo(CoachingStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/coachings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // --- 진행 중 챌린지 (명세 COACH-01 · ERD 7장) ----------------------------

    @Test
    @DisplayName("진행 중 챌린지가 있어도 대화는 시작된다 — 막는 것은 확정(COACH-04)뿐이다")
    void doesNotBlockWhileAChallengeRuns() throws Exception {
        Challenge challenge = givenChallenge(user);

        mockMvc.perform(startRequest("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 챌린지는 그대로다 — 코칭 시작이 챌린지를 건드리지 않는다
        assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.IN_PROGRESS);
    }
}
