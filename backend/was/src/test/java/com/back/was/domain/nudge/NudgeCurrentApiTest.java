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
import jakarta.persistence.EntityManager;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NDG-01 홈 노출 넛지.
 *
 * <p>넛지를 만드는 판정(서버_내부_처리 3장)은 아직 없으므로 카드는 리포지터리로 직접
 * 넣는다. 숫자는 {@link SpendingComparison} 을 거쳐 만들어서 docs/시연_데이터.md 6-1 의
 * 배달 판정(101,000 vs 55/62/58/65천 → 평균 60,000 · Z 9.32 · +68.33%)과 같은 값이 되게
 * 한다 — 기대값을 손으로 적으면 계산이 바뀌어도 테스트가 모른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NudgeCurrentApiTest {

    private static final String EMAIL = "nudge@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final DateRange THIS_WEEK = Weeks.elapsedWeekOf(TODAY);
    private static final DateRange LAST_WEEK = Weeks.elapsedWeeksAgo(TODAY, 1);

    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;

    /** 시연 6-1 의 배달 — 평균 60,000 · Z 9.32 · +68.33% → 넛지 생성 조건 충족. */
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

    private Nudge givenNudge(User owner, long categoryId, DateRange period, SpendingComparison comparison) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        return nudgeRepository.save(Nudge.of(owner, category, period, comparison));
    }

    private Nudge givenNudge(long categoryId, DateRange period) {
        return givenNudge(user, categoryId, period, DELIVERY_SPIKE);
    }

    // --- 응답 ---------------------------------------------------------------

    @Test
    @DisplayName("명세 NDG-01: 이번 주 UNREAD 카드를 모든 필드와 함께 준다 (제목은 템플릿 조합)")
    void returnsThisWeeksUnreadNudge() throws Exception {
        Nudge nudge = givenNudge(DELIVERY, THIS_WEEK);

        mockMvc.perform(authed(get("/nudges/current")))
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
                .andExpect(jsonPath("$.data.updatedAt").value(Matchers.containsString("+09:00")));
    }

    @Test
    @DisplayName("Z-score 는 응답에 없다 (명세 NDG-01 '내부 판정용으로 미포함')")
    void zScoreIsNotExposed() throws Exception {
        givenNudge(DELIVERY, THIS_WEEK);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data.zScore").doesNotExist())
                // 저장은 되어 있다 — 발표·디버깅 근거 (ERD 6장)
                .andExpect(status().isOk());

        assertThat(nudgeRepository.findAll())
                .singleElement()
                .satisfies(saved -> assertThat(saved.getZScore()).isEqualByComparingTo("9.32"));
    }

    @Test
    @DisplayName("넛지가 없으면 data 키를 null 로 남긴다 — 404 가 아니다 (카드 숨김)")
    void noNudgeGivesNullData() throws Exception {
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // --- 노출 조건 (명세 NDG-01) --------------------------------------------

    @Test
    @DisplayName("지난 주 넛지는 미확인이어도 노출되지 않는다")
    void lastWeeksNudgeIsHidden() throws Exception {
        givenNudge(DELIVERY, LAST_WEEK);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("이미 반응한 카드(DISMISSED·VIEWED·COACHING_STARTED)는 노출되지 않는다")
    void onlyUnreadIsExposed() throws Exception {
        Nudge dismissed = givenNudge(DELIVERY, THIS_WEEK);
        dismissed.changeStatus(NudgeStatus.DISMISSED);
        Nudge viewed = givenNudge(CAFE, THIS_WEEK);
        viewed.changeStatus(NudgeStatus.VIEWED);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("여러 개면 updatedAt 이 가장 최근인 1개 (ERD 6장 '홈 노출 순서 기준')")
    void returnsTheMostRecentlyUpdated() throws Exception {
        Nudge delivery = givenNudge(DELIVERY, THIS_WEEK);
        Nudge cafe = givenNudge(CAFE, THIS_WEEK);

        // 두 행을 같은 트랜잭션에서 연달아 넣으면 updatedAt 이 같을 수 있다 — 정렬이
        // 삽입 순서나 ID 로 우연히 맞는 걸 통과로 착각하지 않도록 값을 직접 벌려 놓는다.
        // @UpdateTimestamp 는 JPA 로 바꿀 수 없어 네이티브 UPDATE 를 쓴다.
        backdateUpdatedAt(delivery, 1);
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.name").value("카페"));

        // 반대로 밀면 반대 카드가 나와야 한다 — ID 순이 아니라 updatedAt 순이라는 증거
        backdateUpdatedAt(cafe, 2);
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.name").value("배달"));
    }

    private void backdateUpdatedAt(Nudge nudge, int hoursAgo) {
        entityManager.flush();
        entityManager.createNativeQuery("update nudges set updated_at = :at where id = :id")
                .setParameter("at", LocalDateTime.now().minusHours(hoursAgo))
                .setParameter("id", nudge.getId())
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("다른 사용자의 넛지는 노출되지 않는다")
    void scopedByUser() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "nudge-other@email.com", "irrelevant-hash"));
        givenNudge(other, DELIVERY, THIS_WEEK, DELIVERY_SPIKE);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/nudges/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // --- 엔티티 불변식 ------------------------------------------------------

    @Test
    @DisplayName("한 사용자·한 카테고리·한 주에 카드는 하나다 (복합 UNIQUE)")
    void oneNudgePerCategoryPerWeek() {
        givenNudge(DELIVERY, THIS_WEEK);

        assertThatThrownBy(() -> {
            givenNudge(DELIVERY, THIS_WEEK);
            nudgeRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("표준편차 0(판정 생략)인 비교로는 카드를 만들 수 없다 — Z_SCORE 가 NOT NULL 이다")
    void rejectsComparisonWithoutZScore() {
        SpendingComparison flat = SpendingComparison.of(9_000, List.of(17_500, 17_500, 17_500, 17_500));
        Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();

        assertThat(flat.zScore()).isNull();
        assertThatThrownBy(() -> Nudge.of(user, delivery, THIS_WEEK, flat))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
