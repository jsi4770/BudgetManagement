package com.back.was.domain.nudge;

import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래 변경 후처리 <b>5번</b> 연결 — 넛지가 커밋된 뒤 AI 문구가 채워지는지
 * (서버_내부_처리 1장 순서 5번 · 8장).
 *
 * <p><b>이 클래스만 {@code @Transactional} 이 아니다.</b> 리스너가
 * {@code AFTER_COMMIT} 에 걸려 있어서, 테스트 트랜잭션이 롤백되는 다른 테스트 방식으로는
 * 아예 실행되지 않는다. 그래서 실제로 커밋하고, 넣은 행은 {@link #cleanUp()} 에서 직접
 * 지운다 (JPA 가 아니라 SQL 로 — 트랜잭션 없이 돌아야 한다).
 *
 * <p>{@code @Async} 는 {@link SynchronousExecution} 이 동기 실행기로 바꿔 끼운다. 비동기
 * 그대로 두면 "언젠가 채워진다"를 기다리는 폴링 테스트가 되어 느리고 불안정하다. 바꿔 끼우는
 * 지점이 Boot 의 자동 구성과 겹치지 않는 이유는 {@code AsyncConfig} 설명에 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NudgeTextApiTest {

    /**
     * {@code @Async} 를 호출 스레드에서 그대로 실행하게 만든다 — {@code AFTER_COMMIT} 이
     * 끝나는 시점에 문구가 이미 채워져 있다.
     *
     * <p>{@code Executor} 빈이 생기면 Boot 의 {@code TaskExecutionAutoConfiguration} 이
     * 물러나므로 빈 정의 충돌은 없다.
     */
    @TestConfiguration
    static class SynchronousExecution {

        @Bean
        Executor applicationTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    private static final String EMAIL = "nudge-text@email.com";
    private static final String PASSWORD = "password123!";

    private static final LocalDate TODAY = KstTime.today();
    private static final LocalDate THIS_MONDAY = Weeks.mondayOf(TODAY);

    private static final long DELIVERY = 2L;

    /** 시연 6-1 의 배달 표본 — 평균 60,000 · 표준편차 약 4,397. */
    private static final int[] SAMPLES = {55_000, 62_000, 58_000, 65_000};

    private static final int SPIKE = 101_000;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;
    private long userId;

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
        userId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, EMAIL);

        // 가입일이 표본 첫 주 월요일보다 앞서야 판정이 돌아간다 (ERD 6장 판정 생략 ①).
        jdbcTemplate.update("update users set created_at = ? where id = ?",
                TODAY.minusDays(150).atStartOfDay(), userId);

        // 직전 4주 표본. 후처리를 거치지 않고 깔기만 하므로 SQL 로 바로 넣는다.
        for (int weeksAgo = 1; weeksAgo <= SAMPLES.length; weeksAgo++) {
            insertTransaction(SAMPLES[weeksAgo - 1], THIS_MONDAY.minusWeeks(weeksAgo));
        }
    }

    /** 커밋되는 테스트라 직접 치운다. FK 때문에 자식 테이블부터. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from nudges where user_id = ?", userId);
        jdbcTemplate.update("delete from transactions where user_id = ?", userId);
        jdbcTemplate.update("delete from refresh_token where user_id = ?", userId);
        jdbcTemplate.update("delete from users where id = ?", userId);
    }

    private void insertTransaction(int amount, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        insert into transactions
                            (user_id, category_id, amount, transaction_date, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                userId, DELIVERY, amount, date, now, now);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    /** TX-01 — 후처리가 도는 경로. */
    private void createTransaction(int amount) throws Exception {
        mockMvc.perform(authed(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %d, "transactionDate": "%s"}
                                """.formatted(DELIVERY, amount, TODAY))))
                .andExpect(status().isCreated());
    }

    private long currentNudgeId() throws Exception {
        String body = mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    @Test
    @DisplayName("TX-01 로 넛지가 생기면 커밋 후 AI 문구가 채워져 NDG-02 에 나온다")
    void fillsAiTextAfterCommit() throws Exception {
        createTransaction(SPIKE);
        long nudgeId = currentNudgeId();

        mockMvc.perform(authed(get("/nudges/{id}", nudgeId)))
                .andExpect(status().isOk())
                // Gemini 가 없으니 docs/AI_대체_문구.md 1·2장 템플릿이 채워진다
                .andExpect(jsonPath("$.data.aiAnalysis").value(
                        org.hamcrest.Matchers.containsString("배달")))
                .andExpect(jsonPath("$.data.aiRecommendation").value(
                        org.hamcrest.Matchers.containsString("배달")))
                .andExpect(jsonPath("$.data.aiAnalysis").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("{"))));
    }

    @Test
    @DisplayName("금액이 갱신되면 문구도 다시 생성된다 (ERD 6장)")
    void regeneratesOnRefresh() throws Exception {
        createTransaction(SPIKE);
        long nudgeId = currentNudgeId();

        // 다시 생성됐는지 확실히 보려고 표식을 심어 둔다 — 템플릿은 무작위라 문장 비교로는
        // "같은 문장을 다시 골랐다"와 구분할 수 없다.
        jdbcTemplate.update("update nudges set ai_analysis = '표식' where id = ?", nudgeId);

        createTransaction(20_000);

        String analysis = jdbcTemplate.queryForObject(
                "select ai_analysis from nudges where id = ?", String.class, nudgeId);
        assertThat(analysis).isNotNull().isNotEqualTo("표식").contains("배달");
        assertThat(jdbcTemplate.queryForObject(
                "select spending_amount from nudges where id = ?", Integer.class, nudgeId))
                .isEqualTo(121_000);
    }

    @Test
    @DisplayName("조건에 못 미치면 카드도 문구도 생기지 않는다")
    void nothingToFillWithoutANudge() throws Exception {
        createTransaction(10_000);

        mockMvc.perform(authed(get("/nudges/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from nudges where user_id = ?", Integer.class, userId))
                .isZero();
    }
}
