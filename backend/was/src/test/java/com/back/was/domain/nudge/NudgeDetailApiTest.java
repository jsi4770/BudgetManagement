package com.back.was.domain.nudge;

import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NDG-02 넛지 상세.
 *
 * <p>NDG-01 과 같은 숫자({@link SpendingComparison} 으로 만든 시연 6-1 의 배달 판정)에 AI
 * 문구 두 개가 더 붙는 모양이다. 판정이 아직 없어 카드는 리포지터리로 직접 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NudgeDetailApiTest {

    private static final String EMAIL = "nudge-detail@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final DateRange THIS_WEEK = Weeks.elapsedWeekOf(TODAY);
    private static final DateRange LAST_WEEK = Weeks.elapsedWeeksAgo(TODAY, 1);

    private static final long DELIVERY = 2L;

    private static final SpendingComparison DELIVERY_SPIKE =
            SpendingComparison.of(101_000, List.of(55_000, 62_000, 58_000, 65_000));

    private static final String AI_ANALYSIS =
            "이번 주 3일 동안 배달을 4번 시켰어요. 평소 이맘때보다 41,000원 더 쓴 셈이에요.";
    private static final String AI_RECOMMENDATION =
            "한 번에 확 줄이기보다, 이번 주 배달비 한도를 정해서 가볍게 시작해봐요.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NudgeRepository nudgeRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private String accessToken;
    private User user;

    @BeforeEach
    void signUp() throws Exception {
        accessToken = signUp(EMAIL);
        user = userRepository.findByEmail(EMAIL).orElseThrow();
    }

    private String signUp(String email) throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private Nudge givenNudge(User owner, DateRange period) {
        Category category = categoryRepository.findById(DELIVERY).orElseThrow();
        return nudgeRepository.save(Nudge.of(owner, category, period, DELIVERY_SPIKE));
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 NDG-02: NDG-01 필드 + aiAnalysis · aiRecommendation")
    void returnsNudgeWithAiText() throws Exception {
        Nudge nudge = givenNudge(user, THIS_WEEK);
        nudge.fillAiText(AI_ANALYSIS, AI_RECOMMENDATION);

        mockMvc.perform(authed(get("/nudges/{id}", nudge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(nudge.getId()))
                .andExpect(jsonPath("$.data.category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                .andExpect(jsonPath("$.data.title").value("배달 지출이 평소보다 늘었어요"))
                .andExpect(jsonPath("$.data.analysisStartDate").value(THIS_WEEK.start().toString()))
                .andExpect(jsonPath("$.data.analysisEndDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.spendingAmount").value(101_000))
                .andExpect(jsonPath("$.data.avgAmount").value(60_000))
                .andExpect(jsonPath("$.data.growthRate").value(68.33))
                .andExpect(jsonPath("$.data.status").value("UNREAD"))
                .andExpect(jsonPath("$.data.createdAt").value(Matchers.containsString("+09:00")))
                .andExpect(jsonPath("$.data.updatedAt").value(Matchers.containsString("+09:00")))
                .andExpect(jsonPath("$.data.aiAnalysis").value(AI_ANALYSIS))
                .andExpect(jsonPath("$.data.aiRecommendation").value(AI_RECOMMENDATION));
    }

    @Test
    @DisplayName("AI 문구 생성 전에는 두 필드가 null 이고 키는 남는다 — 프론트의 'AI가 분석 중이에요' 신호")
    void aiTextIsNullBeforeGeneration() throws Exception {
        Nudge nudge = givenNudge(user, THIS_WEEK);

        mockMvc.perform(authed(get("/nudges/{id}", nudge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAnalysis").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.aiRecommendation").value(Matchers.nullValue()))
                // 나머지 필드는 정상이라 카드 자체는 그릴 수 있다
                .andExpect(jsonPath("$.data.spendingAmount").value(101_000));
    }

    @Test
    @DisplayName("Z-score 는 상세에도 없다 — 명세가 더하는 건 AI 문구 두 개뿐이다")
    void zScoreIsNotExposed() throws Exception {
        Nudge nudge = givenNudge(user, THIS_WEEK);

        mockMvc.perform(authed(get("/nudges/{id}", nudge.getId())))
                .andExpect(jsonPath("$.data.zScore").doesNotExist());
    }

    // --- NDG-01 과 다른 점: 노출 조건이 없다 --------------------------------

    @Test
    @DisplayName("지난 주 넛지도 상세는 열린다 — 이번 주 제한은 홈 노출(NDG-01)만의 규칙")
    void lastWeeksNudgeIsStillReadable() throws Exception {
        Nudge nudge = givenNudge(user, LAST_WEEK);

        mockMvc.perform(authed(get("/nudges/{id}", nudge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStartDate").value(LAST_WEEK.start().toString()));
    }

    @Test
    @DisplayName("닫은 넛지도 상세는 열린다 (status 가 그대로 내려간다)")
    void dismissedNudgeIsStillReadable() throws Exception {
        Nudge nudge = givenNudge(user, THIS_WEEK);
        nudge.changeStatus(NudgeStatus.DISMISSED);

        mockMvc.perform(authed(get("/nudges/{id}", nudge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }

    // --- 소유권·검증·인증 ---------------------------------------------------

    @Test
    @DisplayName("없는 id 는 404 NOT_FOUND")
    void unknownIdIsNotFound() throws Exception {
        mockMvc.perform(authed(get("/nudges/{id}", 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 사용자의 넛지는 403 이 아니라 404 — 없는 id 와 응답이 완전히 같다 (명세 0장 5))")
    void otherUsersNudgeIsNotFound() throws Exception {
        Long mine = givenNudge(user, THIS_WEEK).getId();

        accessToken = signUp("nudge-detail-other@email.com");

        String existing = mockMvc.perform(authed(get("/nudges/{id}", mine)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(authed(get("/nudges/{id}", 999999)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(existing).isEqualTo(missing);
    }

    @Test
    @DisplayName("숫자가 아닌 id 는 400 VALIDATION_ERROR")
    void malformedIdIsRejected() throws Exception {
        mockMvc.perform(authed(get("/nudges/{id}", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("NDG-01 회귀: /nudges/current 는 NDG-02 의 {id} 로 해석되지 않는다")
    void currentPathStillWins() throws Exception {
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/nudges/{id}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
