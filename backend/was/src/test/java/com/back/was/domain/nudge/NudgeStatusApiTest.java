package com.back.was.domain.nudge;

import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.nudge.dto.NudgeReaction;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NDG-03 넛지 상태 변경. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NudgeStatusApiTest {

    private static final String EMAIL = "nudge-status@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final DateRange THIS_WEEK = Weeks.elapsedWeekOf(TODAY);

    private static final long DELIVERY = 2L;

    private static final SpendingComparison DELIVERY_SPIKE =
            SpendingComparison.of(101_000, List.of(55_000, 62_000, 58_000, 65_000));

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

    private Nudge givenNudge(User owner) {
        Category category = categoryRepository.findById(DELIVERY).orElseThrow();
        return nudgeRepository.save(Nudge.of(owner, category, THIS_WEEK, DELIVERY_SPIKE));
    }

    private MockHttpServletRequestBuilder patchStatus(Long id, String status) {
        return authed(patch("/nudges/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(status)));
    }

    // --- 상태 변경 ----------------------------------------------------------

    @Test
    @DisplayName("명세 NDG-03: DISMISSED 로 바꾸면 NDG-02 와 같은 모양으로 변경된 status 를 준다")
    void dismiss() throws Exception {
        Nudge nudge = givenNudge(user);
        nudge.fillAiText("분석 문구", "제안 문구");

        mockMvc.perform(patchStatus(nudge.getId(), "DISMISSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(nudge.getId()))
                .andExpect(jsonPath("$.data.status").value("DISMISSED"))
                // NDG-02 와 같은 필드 구성 (AI 문구까지 포함)
                .andExpect(jsonPath("$.data.title").value("배달 지출이 평소보다 늘었어요"))
                .andExpect(jsonPath("$.data.spendingAmount").value(101_000))
                .andExpect(jsonPath("$.data.avgAmount").value(60_000))
                .andExpect(jsonPath("$.data.growthRate").value(68.33))
                .andExpect(jsonPath("$.data.aiAnalysis").value("분석 문구"))
                .andExpect(jsonPath("$.data.aiRecommendation").value("제안 문구"))
                .andExpect(jsonPath("$.data.zScore").doesNotExist());

        assertThat(nudgeRepository.findById(nudge.getId()))
                .get()
                .extracting(Nudge::getStatus)
                .isEqualTo(NudgeStatus.DISMISSED);
    }

    @Test
    @DisplayName("VIEWED · COACHING_STARTED 도 받는다 (명세 NDG-03 표의 세 값)")
    void acceptsEveryReaction() throws Exception {
        for (NudgeReaction reaction : NudgeReaction.values()) {
            Nudge nudge = givenNudge(user);

            mockMvc.perform(patchStatus(nudge.getId(), reaction.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(reaction.name()));

            nudgeRepository.delete(nudge);
            nudgeRepository.flush();
        }
    }

    @Test
    @DisplayName("닫은 카드는 홈에서 사라진다 — NDG-01 과 맞물리는 지점")
    void dismissedNudgeDisappearsFromHome() throws Exception {
        Nudge nudge = givenNudge(user);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data.id").value(nudge.getId()));

        mockMvc.perform(patchStatus(nudge.getId(), "DISMISSED"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("같은 상태를 다시 보내도 200 이다 (명세에 에러가 없다)")
    void sameStatusTwiceIsFine() throws Exception {
        Nudge nudge = givenNudge(user);

        mockMvc.perform(patchStatus(nudge.getId(), "VIEWED")).andExpect(status().isOk());
        mockMvc.perform(patchStatus(nudge.getId(), "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VIEWED"));
    }

    // --- 되돌릴 수 없는 전이 ------------------------------------------------

    @Test
    @DisplayName("UNREAD 로는 되돌릴 수 없다 — 닫은 넛지가 재노출되면 안 된다 (명세 NDG-03)")
    void cannotRevertToUnread() throws Exception {
        Nudge nudge = givenNudge(user);
        mockMvc.perform(patchStatus(nudge.getId(), "DISMISSED")).andExpect(status().isOk());

        mockMvc.perform(patchStatus(nudge.getId(), "UNREAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(nudgeRepository.findById(nudge.getId()))
                .get()
                .extracting(Nudge::getStatus)
                .isEqualTo(NudgeStatus.DISMISSED);
    }

    @Test
    @DisplayName("NudgeReaction 은 UNREAD 를 제외한 모든 상태를 덮는다 — 상태를 추가하면 여기서 걸린다")
    void reactionsCoverEveryStatusButUnread() {
        assertThat(Arrays.stream(NudgeReaction.values()).map(NudgeReaction::toStatus))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(NudgeStatus.values())
                                .filter(status -> status != NudgeStatus.UNREAD)
                                .toList());
    }

    // --- 검증·소유권·인증 ---------------------------------------------------

    @Test
    @DisplayName("알 수 없는 status 는 400 VALIDATION_ERROR")
    void rejectsUnknownStatus() throws Exception {
        Nudge nudge = givenNudge(user);

        mockMvc.perform(patchStatus(nudge.getId(), "LIKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("status 를 빼면 400 VALIDATION_ERROR + details[{field: status, reason: REQUIRED}]")
    void statusIsRequired() throws Exception {
        Nudge nudge = givenNudge(user);

        mockMvc.perform(authed(patch("/nudges/{id}", nudge.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("status"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("다른 사용자의 넛지는 404 — 상태도 바뀌지 않는다")
    void otherUsersNudgeIsNotFound() throws Exception {
        Nudge mine = givenNudge(user);

        accessToken = signUp("nudge-status-other@email.com");

        mockMvc.perform(patchStatus(mine.getId(), "DISMISSED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(nudgeRepository.findById(mine.getId()))
                .get()
                .extracting(Nudge::getStatus)
                .isEqualTo(NudgeStatus.UNREAD);
    }

    @Test
    @DisplayName("없는 id 는 404 NOT_FOUND")
    void unknownIdIsNotFound() throws Exception {
        mockMvc.perform(patchStatus(999999L, "DISMISSED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/nudges/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISMISSED"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
