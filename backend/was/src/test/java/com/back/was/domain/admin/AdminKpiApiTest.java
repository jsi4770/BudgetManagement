package com.back.was.domain.admin;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.user.Role;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.security.JwtProvider;
import com.back.was.global.support.KstTime;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN-01 KPI 조회.
 *
 * <p>기준값은 시연_데이터 4장이다: 종료된 챌린지 4건 중 3건 성공 → <b>75.00%</b>, 목표
 * 70.00% 달성.
 *
 * <p>집계가 <b>프로젝트 전체</b>라 사용자 범위가 없다 — 그래서 다른 사용자의 챌린지가
 * 분모에 들어가는지도 확인한다(들어가야 맞다).
 *
 * <p>ADMIN 토큰은 {@link JwtProvider} 로 직접 만든다. 가입으로는 ADMIN 을 만들 수 없고
 * (ERD 1장) 시더는 dev 프로필 전용이라, 역할 클레임만 있으면 되는 이 테스트에는 토큰을
 * 직접 발급하는 쪽이 간단하다 — 필터가 DB 를 보지 않고 클레임으로 권한을 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminKpiApiTest {

    private static final String EMAIL = "admin-kpi@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();

    private static final long DELIVERY = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ChallengeRepository challengeRepository;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private String userToken;
    private String adminToken;
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

        userToken = JsonPath.read(body, "$.data.accessToken");
        user = userRepository.findByEmail(EMAIL).orElseThrow();

        User admin = userRepository.save(User.admin(
                "관리자", "admin-kpi-test@nudge.test", "irrelevant-hash"));
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN.name());
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    /** 종료된 챌린지 한 건. 실적을 목표 이하/초과로 넣어 판정 결과를 정한다. */
    private void givenFinishedChallenge(User owner, int spendingAmount) {
        Coaching coaching = coachingRepository.save(Coaching.start(owner));
        coaching.cancel();
        Category delivery = categoryRepository.findById(DELIVERY).orElseThrow();

        Challenge challenge = challengeRepository.save(Challenge.of(
                coaching, delivery, "배달비 50,000원 이내", GoalType.AMOUNT, 50_000, null,
                TODAY.minusDays(7), TODAY.minusDays(1)));
        challenge.updatePerformance(spendingAmount, 2);
        challenge.finish();
    }

    private void givenSuccess(User owner) {
        givenFinishedChallenge(owner, 35_000);   // 35,000 ≤ 50,000 → SUCCESS
    }

    private void givenFailure(User owner) {
        givenFinishedChallenge(owner, 56_000);   // 56,000 > 50,000 → FAILED
    }

    // --- 집계 (ERD 13장) ----------------------------------------------------

    @Test
    @DisplayName("명세 ADMIN-01: 종료 4건 중 3건 성공 → 75.00%, 목표 70.00% 달성 (시연 4장)")
    void aggregatesTheDocumentedKpi() throws Exception {
        givenSuccess(user);
        givenSuccess(user);
        givenSuccess(user);
        givenFailure(user);

        mockMvc.perform(asAdmin(get("/admin/kpi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.endedChallengeCount").value(4))
                .andExpect(jsonPath("$.data.successChallengeCount").value(3))
                .andExpect(jsonPath("$.data.achievementRate").value(75.00))
                .andExpect(jsonPath("$.data.target").value(70.00))
                .andExpect(jsonPath("$.data.isAchieved").value(true));
    }

    @Test
    @DisplayName("목표에 못 미치면 isAchieved 가 false — 경계(70%)는 달성으로 센다")
    void achievedIsInclusiveAtTheTarget() throws Exception {
        // 5건 중 3건 = 60% → 미달
        givenSuccess(user);
        givenSuccess(user);
        givenSuccess(user);
        givenFailure(user);
        givenFailure(user);

        mockMvc.perform(asAdmin(get("/admin/kpi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievementRate").value(60.00))
                .andExpect(jsonPath("$.data.isAchieved").value(false));
    }

    @Test
    @DisplayName("진행 중 챌린지는 분모에 들어가지 않는다 (ERD 13장 WHERE)")
    void ignoresRunningChallenges() throws Exception {
        givenSuccess(user);

        // 진행 중 한 건 — 판정하지 않고 둔다
        Coaching coaching = coachingRepository.save(Coaching.start(user));
        coaching.cancel();
        challengeRepository.save(Challenge.of(
                coaching, categoryRepository.findById(DELIVERY).orElseThrow(),
                "진행 중", GoalType.AMOUNT, 50_000, null, TODAY, TODAY.plusDays(6)));

        mockMvc.perform(asAdmin(get("/admin/kpi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endedChallengeCount").value(1))
                .andExpect(jsonPath("$.data.achievementRate").value(100.00));
    }

    @Test
    @DisplayName("집계는 프로젝트 전체다 — 다른 사용자의 챌린지도 들어간다")
    void aggregatesAcrossAllUsers() throws Exception {
        User other = userRepository.save(
                User.signUp("이영희", "admin-kpi-other@email.com", "irrelevant-hash"));
        givenSuccess(user);
        givenFailure(other);

        mockMvc.perform(asAdmin(get("/admin/kpi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endedChallengeCount").value(2))
                .andExpect(jsonPath("$.data.successChallengeCount").value(1))
                .andExpect(jsonPath("$.data.achievementRate").value(50.00));
    }

    @Test
    @DisplayName("종료된 챌린지가 없으면 0건 / 0.00% 다 — 404 나 null 이 아니다")
    void emptyAggregateIsZero() throws Exception {
        mockMvc.perform(asAdmin(get("/admin/kpi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endedChallengeCount").value(0))
                .andExpect(jsonPath("$.data.successChallengeCount").value(0))
                // null 이면 화면 게이지가 NaN 이 된다 (ANL-01 의 budgetUsageRate 와 같은 판단)
                .andExpect(jsonPath("$.data.achievementRate").value(0.00))
                .andExpect(jsonPath("$.data.isAchieved").value(false));
    }

    @Test
    @DisplayName("비율은 소수점 2자리로 반올림한다 (1/3 → 33.33)")
    void roundsToTwoDecimals() throws Exception {
        givenSuccess(user);
        givenFailure(user);
        givenFailure(user);

        mockMvc.perform(asAdmin(get("/admin/kpi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievementRate").value(33.33));
    }

    // --- 권한 (명세 ADMIN-01) -----------------------------------------------

    @Nested
    @DisplayName("권한")
    class Authorization {

        @Test
        @DisplayName("일반 사용자는 403 FORBIDDEN — 이 프로젝트에서 403 이 나는 유일한 경로다")
        void regularUserIsForbidden() throws Exception {
            mockMvc.perform(get("/admin/kpi")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("토큰이 없으면 403 이 아니라 401 이다 — 권한 부족과 미인증은 다르다")
        void withoutATokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/admin/kpi"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        /**
         * 비밀번호가 문서에 적힌 계정을 운영에 띄우면 그게 백도어다. 운영에서는 관리자를
         * 의도적으로 만들어야 하고, 그때까지 ADMIN-01 은 존재하지만 아무도 통과하지 못한다.
         *
         * <p>근거는 두 가지고, <b>둘 다 실행 순서와 무관해야</b> 한다: 이 컨텍스트에
         * 시더 빈이 없다는 것과, 가입이 {@code USER} 를 하드코딩한다는 것(ERD 1장).
         * 둘을 합치면 기본 프로필에서 {@code ADMIN} 을 만드는 경로가 남지 않는다.
         *
         * <p><b>"{@code users} 에 관리자 행이 없다"는 여기서 단정할 수 없다.</b> 테스트
         * 전체가 인메모리 H2 하나({@code jdbc:h2:mem:was;DB_CLOSE_DELAY=-1})를 컨텍스트와
         * 무관하게 공유하는데, <b>dev 컨텍스트가 뜨는 순간 {@code AdminSeeder} 가 그 공용
         * DB 에 관리자를 커밋</b>한다({@code ApplicationRunner} 라 테스트 트랜잭션 밖이다).
         * 그러면 캐시된 이 컨텍스트의 테스트도 그 행을 보게 된다 — 즉 그 단정은 프로필의
         * 성질이 아니라 "이 실행에서 dev 컨텍스트가 아직 안 떴다"는 <b>실행 순서</b>의
         * 성질이었고, 순서가 바뀌면 깨졌다(실제로 깨졌다). dev 쪽의 긍정 확인은
         * {@code AdminSeederTest} 가 맡는다.
         */
        @Test
        @DisplayName("dev 프로필이 아니면 관리자 시더 빈이 없다 — 운영에 관리자가 자동 생성되지 않는다")
        void theAdminSeederIsDevOnly() {
            assertThat(applicationContext.getBeanNamesForType(AdminSeeder.class)).isEmpty();
            assertThat(user.getRole()).isEqualTo(Role.USER);
        }
    }
}
