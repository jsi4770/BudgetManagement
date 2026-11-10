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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHL-01 챌린지 목록.
 *
 * <p>챌린지를 만드는 쪽(COACH-04)과 실적을 갱신하는 쪽(후처리 2번)은 각자의 테스트가
 * 있으므로 여기서는 리포지터리로 직접 넣는다. 기준값은 명세 CHL-01 예시 그대로다:
 * 배달 / 목표 50,000원 / 11-04 ~ 11-10 / 사용 24,000원 1회 → 48.00% / {@code daysLeft} 6.
 * 날짜만 오늘(KST)로 옮긴다 — 고정 날짜를 쓰면 {@code daysLeft} 가 날마다 달라진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeListApiTest {

    private static final String EMAIL = "challenge-list@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    private static final long FOOD = 1L;
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

    /** 확정된 챌린지 하나. 코칭을 거쳐 사용자에 연결된다(ERD 9장 — CHALLENGE 에 USER_ID 없음). */
    private Challenge givenChallenge(User owner, long categoryId, String title,
                                     LocalDate start, LocalDate end) {
        Coaching coaching = coachingRepository.save(Coaching.start(owner));
        coaching.cancel();
        Category category = categoryRepository.findById(categoryId).orElseThrow();

        return challengeRepository.save(Challenge.of(
                coaching, category, title, GoalType.AMOUNT, 50_000, null, start, end));
    }

    /** 명세 CHL-01 예시의 챌린지 — 오늘 시작하는 7일 배달 챌린지. */
    private Challenge givenDeliveryChallenge() {
        return givenChallenge(user, DELIVERY, "배달비 50,000원 이내", TODAY, TODAY.plusDays(6));
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 CHL-01: data 가 바로 배열이고 항목에 모든 필드가 있다")
    void returnsChallengesAsABareArray() throws Exception {
        Challenge challenge = givenDeliveryChallenge();
        challenge.updatePerformance(24_000, 1);

        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 페이지 정보가 없다 — CAT-01 과 같은 모양 (명세 CHL-01 예시)
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(challenge.getId()))
                .andExpect(jsonPath("$.data[0].title").value("배달비 50,000원 이내"))
                .andExpect(jsonPath("$.data[0].category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data[0].category.name").value("배달"))
                // 챌린지 카테고리는 항상 지출이라 type 은 내보내지 않는다
                .andExpect(jsonPath("$.data[0].category.type").doesNotExist())
                .andExpect(jsonPath("$.data[0].startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data[0].endDate").value(TODAY.plusDays(6).toString()))
                .andExpect(jsonPath("$.data[0].goalType").value("AMOUNT"))
                .andExpect(jsonPath("$.data[0].goalAmount").value(50_000))
                .andExpect(jsonPath("$.data[0].goalCount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].spendingAmount").value(24_000))
                .andExpect(jsonPath("$.data[0].spendingCount").value(1))
                .andExpect(jsonPath("$.data[0].achievementRate").value(48.00))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].daysLeft").value(6));
    }

    @Test
    @DisplayName("코칭 ID 는 응답에 없다 — 화면이 쓰지 않고 남의 코칭 ID 추측 재료가 된다")
    void doesNotExposeTheCoachingId() throws Exception {
        givenDeliveryChallenge();

        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].coachingId").doesNotExist())
                .andExpect(jsonPath("$.data[0].coaching").doesNotExist());
    }

    @Test
    @DisplayName("챌린지가 없으면 빈 배열이다 — 404 가 아니다")
    void noChallengeGivesAnEmptyArray() throws Exception {
        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", Matchers.empty()))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("실적을 갱신하지 않은 챌린지는 0 과 0.00 으로 나간다")
    void freshChallengeReportsZeroes() throws Exception {
        givenDeliveryChallenge();

        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].spendingAmount").value(0))
                .andExpect(jsonPath("$.data[0].spendingCount").value(0))
                .andExpect(jsonPath("$.data[0].achievementRate").value(0.00));
    }

    // --- daysLeft (명세 CHL-01 "서버 계산, 미저장") ---------------------------

    @Nested
    @DisplayName("daysLeft")
    class DaysLeft {

        @Test
        @DisplayName("종료일 당일은 0 이다 — 기간의 길이가 아니라 '남은 일수'다")
        void isZeroOnTheLastDay() throws Exception {
            givenChallenge(user, DELIVERY, "오늘 끝나는 챌린지", TODAY.minusDays(6), TODAY);

            mockMvc.perform(authed(get("/challenges")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].daysLeft").value(0));
        }

        @Test
        @DisplayName("종료일이 지났으면 음수가 아니라 0 이다 — 화면의 D-N 표기가 깨지지 않게")
        void neverGoesNegative() throws Exception {
            Challenge past = givenChallenge(
                    user, DELIVERY, "지난 챌린지", TODAY.minusDays(30), TODAY.minusDays(24));
            past.finish();

            mockMvc.perform(authed(get("/challenges")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].daysLeft").value(0))
                    // 종료 여부는 status 가 말해 준다
                    .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
        }
    }

    // --- status 필터 (명세 CHL-01) -------------------------------------------

    @Nested
    @DisplayName("status 필터")
    class StatusFilter {

        @Test
        @DisplayName("status 를 주면 그 상태만, 없으면 전체")
        void filtersByStatus() throws Exception {
            givenDeliveryChallenge();
            Challenge finished = givenChallenge(
                    user, FOOD, "지난 식비 챌린지", TODAY.minusDays(30), TODAY.minusDays(24));
            finished.updatePerformance(60_000, 4);
            finished.finish();   // 60,000 > 50,000 → FAILED

            mockMvc.perform(authed(get("/challenges")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", Matchers.hasSize(2)));

            mockMvc.perform(authed(get("/challenges").param("status", "IN_PROGRESS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"));

            mockMvc.perform(authed(get("/challenges").param("status", "FAILED")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data[0].status").value("FAILED"));

            // 완료 목록은 프론트가 전체를 받아 SUCCESS·FAILED 로 걸러 쓴다 (명세 CHL-01)
            mockMvc.perform(authed(get("/challenges").param("status", "SUCCESS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", Matchers.empty()));
        }

        @Test
        @DisplayName("없는 상태값이면 400 VALIDATION_ERROR")
        void rejectsAnUnknownStatus() throws Exception {
            mockMvc.perform(authed(get("/challenges").param("status", "FOO")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("빈 문자열은 '전체'다 — Spring 이 빈 값을 null 로 변환한다 (CAT-01 type 과 동일)")
        void treatsAnEmptyStatusAsAll() throws Exception {
            givenDeliveryChallenge();

            // ANL-01 의 yearMonth 는 `?yearMonth=` 를 400 으로 막지만, 그쪽은 직접 파싱하는
            // String 파라미터다. 선택 enum 파라미터는 CAT-01 의 type 과 같은 변환 규칙을
            // 따르는 쪽이 맞다 — 한 API 만 더 엄격하게 만들 이유가 명세에 없다.
            mockMvc.perform(authed(get("/challenges").param("status", "")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", Matchers.hasSize(1)));
        }
    }

    // --- 정렬·범위 ----------------------------------------------------------

    @Test
    @DisplayName("최신 확정 순이다 (명세에 없어 구현이 정한 순서) — 실적이 갱신돼도 흔들리지 않는다")
    void ordersByConfirmedAtDescending() throws Exception {
        Challenge older = givenChallenge(
                user, FOOD, "먼저 확정", TODAY.minusDays(30), TODAY.minusDays(24));
        Challenge newer = givenDeliveryChallenge();

        // 같은 트랜잭션에서 연달아 넣으면 createdAt 이 같을 수 있다 — 정렬이 삽입 순서나
        // ID 로 우연히 맞는 걸 통과로 착각하지 않도록 값을 직접 벌려 놓는다.
        backdateCreatedAt(older, 2);
        backdateCreatedAt(newer, 1);

        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("배달비 50,000원 이내"))
                .andExpect(jsonPath("$.data[1].title").value("먼저 확정"));

        // 반대로 밀면 순서가 뒤집혀야 한다 — ID 순이 아니라 createdAt 순이라는 증거
        backdateCreatedAt(newer, 3);
        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("먼저 확정"));
    }

    private void backdateCreatedAt(Challenge challenge, int hoursAgo) {
        entityManager.flush();
        entityManager.createNativeQuery("update challenges set created_at = :at where id = :id")
                .setParameter("at", LocalDateTime.now().minusHours(hoursAgo))
                .setParameter("id", challenge.getId())
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("다른 사용자의 챌린지는 목록에 섞이지 않는다 (COACHING.USER_ID 경유)")
    void scopedByUser() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "challenge-list-other@email.com", "irrelevant-hash"));
        givenChallenge(other, DELIVERY, "남의 챌린지", TODAY, TODAY.plusDays(6));

        mockMvc.perform(authed(get("/challenges")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.empty()));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/challenges"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
