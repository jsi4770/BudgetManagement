package com.back.was.domain.transaction;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래 변경 후처리 연결 (서버_내부_처리 1장 순서 3번) — TX-01·05·06 을 호출하면 넛지가
 * 실제로 생기고·갱신되고·사라지는지.
 *
 * <p>판정 로직 자체는 {@code NudgeJudgementTest} 가 검증한다. 여기서 보는 것은 <b>연결</b>
 * 이다: 세 엔드포인트가 같은 트랜잭션에서 판정을 돌리는지, TX-05 가 수정 전 카테고리까지
 * 재판정하는지, NDG-03 으로 닫은 카드가 재판정 후에도 닫혀 있는지.
 *
 * <p>직전 4주 표본은 리포지터리로 깔고, <b>이번 주 거래만 API 로</b> 넣는다 — 후처리가
 * 도는 경로가 그쪽이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionNudgeApiTest {

    private static final String EMAIL = "tx-nudge@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate THIS_MONDAY = Weeks.mondayOf(TODAY);

    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;

    /** 시연 6-1 의 배달 표본 — 평균 60,000 · 표준편차 약 4,397. */
    private static final int[] SAMPLES = {55_000, 62_000, 58_000, 65_000};

    /** 이 금액이면 Z 9.32 · 차이 41,000 으로 생성 조건을 넘는다. */
    private static final int SPIKE = 101_000;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransactionRepository transactionRepository;

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
    void signUpLongTimeMember() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = JsonPath.read(body, "$.data.accessToken");
        user = userRepository.findByEmail(EMAIL).orElseThrow();

        // 가입 직후 계정은 4주치 기록이 없어 판정이 생략된다 (ERD 6장 판정 생략 ①).
        entityManager.flush();
        entityManager.createNativeQuery("update users set created_at = :at where id = :id")
                .setParameter("at", TODAY.minusDays(150).atStartOfDay())
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.clear();
        user = userRepository.findById(user.getId()).orElseThrow();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    /** 직전 4주 표본을 각 주 월요일에 — API 가 아니라 리포지터리로 (후처리 없이 깔기만). */
    private void givenSamples(long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        for (int weeksAgo = 1; weeksAgo <= SAMPLES.length; weeksAgo++) {
            transactionRepository.save(Transaction.of(
                    user, category, SAMPLES[weeksAgo - 1], THIS_MONDAY.minusWeeks(weeksAgo), null));
        }
    }

    private long createTransaction(long categoryId, int amount) throws Exception {
        String body = mockMvc.perform(authed(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %d, "transactionDate": "%s"}
                                """.formatted(categoryId, amount, TODAY))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long currentNudgeId() throws Exception {
        String body = mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    // --- TX-01 등록 ---------------------------------------------------------

    @Test
    @DisplayName("TX-01: 거래를 등록하면 넛지가 생기고 NDG-01 에 바로 뜬다")
    void createTriggersJudgement() throws Exception {
        givenSamples(DELIVERY);

        createTransaction(DELIVERY, SPIKE);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data.title").value("배달 지출이 평소보다 늘었어요"))
                .andExpect(jsonPath("$.data.spendingAmount").value(SPIKE))
                .andExpect(jsonPath("$.data.avgAmount").value(60_000))
                .andExpect(jsonPath("$.data.growthRate").value(68.33))
                .andExpect(jsonPath("$.data.status").value("UNREAD"));
    }

    @Test
    @DisplayName("TX-01: 조건에 못 미치는 거래는 카드를 만들지 않는다")
    void createBelowThresholdCreatesNothing() throws Exception {
        givenSamples(DELIVERY);

        createTransaction(DELIVERY, 10_000);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("TX-01: 거래를 더 등록하면 같은 카드가 갱신된다 (새 카드가 생기지 않는다)")
    void secondCreateRefreshesTheSameNudge() throws Exception {
        givenSamples(DELIVERY);
        createTransaction(DELIVERY, SPIKE);
        long nudgeId = currentNudgeId();

        createTransaction(DELIVERY, 20_000);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data.id").value(nudgeId))
                .andExpect(jsonPath("$.data.spendingAmount").value(121_000));
        assertThat(nudgeRepository.findAll()).hasSize(1);
    }

    // --- TX-06 삭제 ---------------------------------------------------------

    @Test
    @DisplayName("TX-06: 거래를 지우면 조건이 꺾여 UNREAD 카드가 사라진다")
    void deleteRemovesTheNudge() throws Exception {
        givenSamples(DELIVERY);
        long transactionId = createTransaction(DELIVERY, SPIKE);
        assertThat(nudgeRepository.findAll()).hasSize(1);

        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        assertThat(nudgeRepository.findAll()).isEmpty();
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    // --- TX-05 수정 ---------------------------------------------------------

    @Test
    @DisplayName("TX-05: 금액을 줄여 조건이 꺾이면 카드가 사라진다")
    void updateAmountRemovesTheNudge() throws Exception {
        givenSamples(DELIVERY);
        long transactionId = createTransaction(DELIVERY, SPIKE);

        mockMvc.perform(authed(patch("/transactions/{id}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10000}
                                """)))
                .andExpect(status().isOk());

        assertThat(nudgeRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("TX-05: 카테고리를 바꾸면 이전·새 카테고리를 둘 다 재판정한다 (서버_내부_처리 1장)")
    void updateCategoryJudgesBothSides() throws Exception {
        givenSamples(DELIVERY);
        givenSamples(CAFE);
        long transactionId = createTransaction(DELIVERY, SPIKE);
        assertThat(nudgeRepository.findAll())
                .singleElement()
                .satisfies(nudge -> assertThat(nudge.getCategory().getId()).isEqualTo(DELIVERY));

        mockMvc.perform(authed(patch("/transactions/{id}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d}
                                """.formatted(CAFE))))
                .andExpect(status().isOk());

        // 이전 카테고리(배달)는 이번 주 0원이 되어 카드가 지워지고, 새 카테고리(카페)에 생긴다
        assertThat(nudgeRepository.findAll())
                .singleElement()
                .satisfies(nudge -> assertThat(nudge.getCategory().getId()).isEqualTo(CAFE));
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data.category.name").value("카페"))
                .andExpect(jsonPath("$.data.title").value("카페 지출이 평소보다 늘었어요"));
    }

    @Test
    @DisplayName("TX-05: 지난 주 거래를 고쳐도 재판정한다 — 표본이 바뀌어 평균이 움직인다")
    void updatingAPastWeekTransactionStillRejudges() throws Exception {
        givenSamples(DELIVERY);
        createTransaction(DELIVERY, SPIKE);
        assertThat(nudgeRepository.findAll()).hasSize(1);

        // 표본 네 주를 전부 101,000 으로 끌어올리면 평균이 올라가 조건이 꺾인다.
        // (지난 주 거래는 API 로 넣을 수 없어 직접 저장했으므로 수정은 TX-05 로 한다)
        var pastTransactions = transactionRepository.search(
                        user.getId(), THIS_MONDAY.minusWeeks(4), THIS_MONDAY.minusDays(1),
                        null, DELIVERY, PageRequest.of(0, 10))
                .getContent();
        for (Transaction past : pastTransactions) {
            mockMvc.perform(authed(patch("/transactions/{id}", past.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount": 101000}
                                    """)))
                    .andExpect(status().isOk());
        }

        assertThat(nudgeRepository.findAll()).isEmpty();
    }

    // --- NDG-03 과의 연결 ---------------------------------------------------

    @Test
    @DisplayName("닫은 카드는 재판정해도 닫힌 채 숫자만 갱신된다 (ERD 6장 '갱신 시 STATUS 유지')")
    void dismissedNudgeSurvivesRejudgement() throws Exception {
        givenSamples(DELIVERY);
        createTransaction(DELIVERY, SPIKE);
        long nudgeId = currentNudgeId();

        mockMvc.perform(authed(patch("/nudges/{id}", nudgeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISMISSED"}
                                """)))
                .andExpect(status().isOk());

        createTransaction(DELIVERY, 20_000);

        assertThat(nudgeRepository.findAll()).singleElement().satisfies(nudge -> {
            assertThat(nudge.getId()).isEqualTo(nudgeId);
            assertThat(nudge.getStatus()).isEqualTo(NudgeStatus.DISMISSED);
            assertThat(nudge.getSpendingAmount()).isEqualTo(121_000);
        });
        // 닫은 카드는 홈에 다시 뜨지 않는다
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("닫은 카드는 조건이 꺾여도 지워지지 않는다 — 삭제 대상은 UNREAD 뿐이다")
    void dismissedNudgeIsNotDeleted() throws Exception {
        givenSamples(DELIVERY);
        long transactionId = createTransaction(DELIVERY, SPIKE);
        long nudgeId = currentNudgeId();
        mockMvc.perform(authed(patch("/nudges/{id}", nudgeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISMISSED"}
                                """)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(delete("/transactions/{id}", transactionId)))
                .andExpect(status().isNoContent());

        assertThat(nudgeRepository.findAll())
                .singleElement()
                .extracting(Nudge::getStatus)
                .isEqualTo(NudgeStatus.DISMISSED);
    }

    // --- 가입 직후 계정 -----------------------------------------------------

    @Test
    @DisplayName("가입 직후 계정은 4주치 기록이 없어 거래를 넣어도 카드가 생기지 않는다")
    void newMemberGetsNoNudge() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "신입", "email": "tx-nudge-new@email.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(body, "$.data.accessToken");

        createTransaction(DELIVERY, SPIKE);

        assertThat(nudgeRepository.findAll()).isEmpty();
        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }
}
