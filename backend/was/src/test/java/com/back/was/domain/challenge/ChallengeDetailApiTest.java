package com.back.was.domain.challenge;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.GoalType;
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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHL-02 챌린지 상세.
 *
 * <p>가장 중요한 한 가지는 <b>CHL-01 항목과 같은 형식</b>이라는 것이고(명세 CHL-02 머리말),
 * {@code hasTheSameShapeAsTheListItem} 이 두 응답을 직접 비교해서 그걸 고정한다 — 한쪽에만
 * 필드를 추가하면 거기서 깨진다. 필드별 값 자체는
 * {@code ChallengeListApiTest} 가 명세 예시로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeDetailApiTest {

    private static final String EMAIL = "challenge-detail@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    private static final long DELIVERY = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChallengeRepository challengeRepository;

    @Autowired
    CoachingRepository coachingRepository;

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

    private Challenge givenChallenge(User owner, LocalDate start, LocalDate end) {
        Coaching coaching = coachingRepository.save(Coaching.start(owner));
        coaching.cancel();
        Category category = categoryRepository.findById(DELIVERY).orElseThrow();

        return challengeRepository.save(Challenge.of(
                coaching, category, "배달비 50,000원 이내", GoalType.AMOUNT, 50_000, null,
                start, end));
    }

    /** 명세 CHL-02 예시의 챌린지 — 오늘 시작하는 7일 배달 챌린지, 사용 24,000원 1회. */
    private Challenge givenDeliveryChallenge() {
        Challenge challenge = givenChallenge(user, TODAY, TODAY.plusDays(6));
        challenge.updatePerformance(24_000, 1);
        return challenge;
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 CHL-02: 배열이 아닌 단일 객체로 모든 필드를 준다")
    void returnsASingleObject() throws Exception {
        Challenge challenge = givenDeliveryChallenge();

        mockMvc.perform(authed(get("/challenges/{id}", challenge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // CHL-01 과 달리 배열이 아니다
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.id").value(challenge.getId()))
                .andExpect(jsonPath("$.data.title").value("배달비 50,000원 이내"))
                .andExpect(jsonPath("$.data.category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                .andExpect(jsonPath("$.data.startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.endDate").value(TODAY.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.goalType").value("AMOUNT"))
                .andExpect(jsonPath("$.data.goalAmount").value(50_000))
                .andExpect(jsonPath("$.data.goalCount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.spendingAmount").value(24_000))
                .andExpect(jsonPath("$.data.spendingCount").value(1))
                .andExpect(jsonPath("$.data.achievementRate").value(48.00))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.daysLeft").value(6))
                // CHL-01 과 같은 이유로 코칭 ID 는 나가지 않는다
                .andExpect(jsonPath("$.data.coachingId").doesNotExist());
    }

    @Test
    @DisplayName("CHL-01 항목과 같은 형식이다 — 목록 원소와 상세의 data 가 똑같다")
    void hasTheSameShapeAsTheListItem() throws Exception {
        Challenge challenge = givenDeliveryChallenge();

        String list = mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String detail = mockMvc.perform(authed(get("/challenges/{id}", challenge.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 목록에서 고른 항목을 상세로 열었을 때 숫자가 달라지면 설명할 수 없다.
        // 한쪽에만 필드를 추가하면 여기서 깨진다 (명세 CHL-02 "CHL-01 항목과 같은 형식").
        Map<String, Object> listItem = JsonPath.read(list, "$.data[0]");
        Map<String, Object> detailData = JsonPath.read(detail, "$.data");
        assertThat(detailData).isEqualTo(listItem);
    }

    @Test
    @DisplayName("종료된 챌린지도 열린다 — 성장 리포트(CHL-03)로 가는 길이다")
    void finishedChallengeIsStillReadable() throws Exception {
        Challenge finished = givenChallenge(user, TODAY.minusDays(30), TODAY.minusDays(24));
        finished.updatePerformance(60_000, 4);
        finished.finish();   // 60,000 > 50,000 → FAILED

        mockMvc.perform(authed(get("/challenges/{id}", finished.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.achievementRate").value(120.00))
                // 종료일이 지났어도 음수가 아니라 0 이다
                .andExpect(jsonPath("$.data.daysLeft").value(0));
    }

    // --- 접근 범위 ----------------------------------------------------------

    @Nested
    @DisplayName("조회 조건 (명세 CHL-02 Errors · 0장 5))")
    class Access {

        @Test
        @DisplayName("없는 챌린지와 남의 챌린지는 똑같이 404 다")
        void unknownOrOthersChallengeIsNotFound() throws Exception {
            User other = userRepository.save(
                    User.signUp("이영희", "challenge-detail-other@email.com", "irrelevant-hash"));
            Challenge othersChallenge = givenChallenge(other, TODAY, TODAY.plusDays(6));

            String missing = mockMvc.perform(authed(get("/challenges/{id}", 999_999)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();

            String others = mockMvc.perform(authed(get("/challenges/{id}", othersChallenge.getId())))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            // 바이트 단위로 같아야 한다 — 다르면 ID 를 훑어 남의 챌린지 존재를 알 수 있다
            assertThat(others).isEqualTo(missing);
        }

        @Test
        @DisplayName("ID 가 숫자가 아니면 400 VALIDATION_ERROR")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(authed(get("/challenges/{id}", "abc")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            Challenge challenge = givenDeliveryChallenge();

            mockMvc.perform(get("/challenges/{id}", challenge.getId()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
