package com.back.was.domain.coaching;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACH-02 코칭 조회 (재진입 복원).
 *
 * <p>대화와 제안을 만드는 쪽(COACH-03)이 아직 없으므로 메시지와 제안은 엔티티로 직접
 * 넣는다 — {@code CoachingMessage.user/ai} 와 {@code Coaching.propose} 가 COACH-03 이 쓸
 * 바로 그 메서드들이다.
 *
 * <p>가장 중요한 한 가지는 <b>COACH-01 과 같은 형식</b>이라는 것이고(명세 COACH-02 머리말),
 * {@code restoresTheSameShapeAsStart} 가 두 응답의 {@code data} 를 직접 비교해서 그걸
 * 고정한다 — 필드 하나를 한쪽에만 추가하면 거기서 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachingDetailApiTest {

    private static final String EMAIL = "coaching-detail@email.com";
    private static final String PASSWORD = "password123!";

    private static final long DELIVERY = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CoachingMessageRepository coachingMessageRepository;

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

    private Coaching givenCoaching(User owner) {
        return coachingRepository.save(Coaching.start(owner));
    }

    private CoachingMessage givenAiMessage(Coaching coaching, String content) {
        return coachingMessageRepository.save(CoachingMessage.ai(coaching, content));
    }

    private CoachingMessage givenUserMessage(Coaching coaching, String content) {
        return coachingMessageRepository.save(CoachingMessage.user(coaching, content));
    }

    /** 명세 COACH-02 예시의 제안 — 배달 / 50,000원 / 7일. */
    private void givenProposal(Coaching coaching) {
        Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();
        coaching.propose(delivery, "배달비 50,000원 이내", GoalType.AMOUNT, 50_000, null, 7);
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 COACH-02: 전체 메시지와 저장된 최신 제안을 함께 준다")
    void restoresTheConversation() throws Exception {
        Coaching coaching = givenCoaching(user);
        givenAiMessage(coaching, "홍길동님, 이번 주 배달 지출이 늘었어요. 어떤 점이 신경 쓰이세요?");
        givenUserMessage(coaching, "퇴근 후 배달이 잦아요");
        givenAiMessage(coaching, "그럴 수 있어요! 지킬 수 있는 목표부터 제안해볼게요.");
        givenProposal(coaching);

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.coachingId").value(coaching.getId()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.createdAt").value(Matchers.containsString("+09:00")))
                .andExpect(jsonPath("$.data.proposal.category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data.proposal.category.name").value("배달"))
                .andExpect(jsonPath("$.data.proposal.goalType").value("AMOUNT"))
                .andExpect(jsonPath("$.data.proposal.goalAmount").value(50_000))
                // goalType 이 AMOUNT 면 goalCount 는 null 이지만 키는 남는다 (명세 예시)
                .andExpect(jsonPath("$.data.proposal.goalCount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.proposal.periodDays").value(7))
                .andExpect(jsonPath("$.data.proposal.title").value("배달비 50,000원 이내"))
                .andExpect(jsonPath("$.data.messages", Matchers.hasSize(3)));
    }

    @Test
    @DisplayName("메시지는 createdAt 오름차순 — 같은 초에 저장돼도 질문이 대답보다 앞이다")
    void ordersMessagesChronologically() throws Exception {
        Coaching coaching = givenCoaching(user);

        // COACH-03 은 사용자 메시지와 AI 응답을 한 요청에서 연달아 저장한다. CREATED_AT 이
        // 초 단위라 두 행의 값이 같아질 수 있고, 그때 ID 가 순서를 정한다 — 뒤집히면
        // 화면에 대답이 질문보다 위에 뜬다.
        givenAiMessage(coaching, "첫 번째");
        givenUserMessage(coaching, "두 번째");
        givenAiMessage(coaching, "세 번째");

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").value("첫 번째"))
                .andExpect(jsonPath("$.data.messages[0].role").value("AI"))
                .andExpect(jsonPath("$.data.messages[1].content").value("두 번째"))
                .andExpect(jsonPath("$.data.messages[1].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[2].content").value("세 번째"));
    }

    @Test
    @DisplayName("제안이 없으면 proposal 은 null 키로 남는다 — 제안 카드 없음의 신호다")
    void noProposalGivesNullKey() throws Exception {
        Coaching coaching = givenCoaching(user);
        givenAiMessage(coaching, "안녕하세요!");

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("제안이 여러 번이면 최신 1개만 복원한다 (ERD 7장 '덮어씀')")
    void restoresOnlyTheLatestProposal() throws Exception {
        Coaching coaching = givenCoaching(user);
        givenProposal(coaching);
        coaching.propose(categoryRepository.findById(DELIVERY).orElseThrow(),
                "배달 2회 이하", GoalType.COUNT, null, 2, 14);

        mockMvc.perform(authed(get("/coachings/{id}", coaching.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal.goalType").value("COUNT"))
                .andExpect(jsonPath("$.data.proposal.goalCount").value(2))
                .andExpect(jsonPath("$.data.proposal.periodDays").value(14))
                // 이전 제안의 금액이 남아 있으면 화면에 금액과 횟수가 동시에 뜬다
                .andExpect(jsonPath("$.data.proposal.goalAmount").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("COACH-01 과 같은 형식이다 — 시작 직후 조회하면 data 가 똑같다")
    void restoresTheSameShapeAsStart() throws Exception {
        String started = mockMvc.perform(authed(post("/coachings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long coachingId = ((Number) JsonPath.read(started, "$.data.coachingId")).longValue();

        String fetched = mockMvc.perform(authed(get("/coachings/{id}", coachingId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 상태 코드만 다르고(201 / 200) data 는 같아야 한다 — 명세 COACH-02
        // "COACH-01과 같은 형식". 한쪽에만 필드를 더하면 여기서 깨진다.
        Map<String, Object> startedData = JsonPath.read(started, "$.data");
        Map<String, Object> fetchedData = JsonPath.read(fetched, "$.data");
        assertThat(fetchedData).isEqualTo(startedData);
    }

    // --- 접근 범위 ----------------------------------------------------------

    @Nested
    @DisplayName("조회 조건 (명세 0장 5) · COACH-02)")
    class Access {

        @Test
        @DisplayName("없는 코칭과 남의 코칭은 똑같이 404 다")
        void unknownOrOthersCoachingIsNotFound() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "coaching-detail-other@email.com", "irrelevant-hash"));
            Coaching othersCoaching = givenCoaching(other);
            givenAiMessage(othersCoaching, "남의 대화 내용");

            String missing = mockMvc.perform(authed(get("/coachings/{id}", 999_999)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();

            String others = mockMvc.perform(authed(get("/coachings/{id}", othersCoaching.getId())))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            // 바이트 단위로 같아야 한다 — 다르면 ID 를 훑어 남의 코칭 존재를 알 수 있다
            assertThat(others).isEqualTo(missing);
        }

        @Test
        @DisplayName("종료된 코칭도 열린다 — 409 COACHING_ALREADY_ENDED 는 쓰기(COACH-03·04) 규칙이다")
        void endedCoachingIsStillReadable() throws Exception {
            Coaching canceled = givenCoaching(user);
            givenAiMessage(canceled, "확정 없이 끝난 대화");
            canceled.cancel();

            mockMvc.perform(authed(get("/coachings/{id}", canceled.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELED"))
                    .andExpect(jsonPath("$.data.messages", Matchers.hasSize(1)));
        }

        @Test
        @DisplayName("ID 가 숫자가 아니면 400 VALIDATION_ERROR")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(authed(get("/coachings/{id}", "abc")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            Coaching coaching = givenCoaching(user);

            mockMvc.perform(get("/coachings/{id}", coaching.getId()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
