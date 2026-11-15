package com.back.was.domain.coaching;

import com.back.was.domain.ai.CoachingOpeningGenerator;
import com.back.was.domain.ai.CoachingReply;
import com.back.was.domain.ai.CoachingReplyGenerator;
import com.back.was.domain.ai.ProposedGoal;
import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACH-01·03 의 <b>트랜잭션 경계</b> — AI 호출이 트랜잭션 밖에서, DB 커넥션 없이
 * 일어나는지 (위험_구간 2번).
 *
 * <p><b>이 클래스는 {@code @Transactional} 이 아니다.</b> 테스트 트랜잭션이 열려 있으면
 * 서비스의 {@code TransactionTemplate} 들이 거기 합류해서, 분리를 지워도 이 테스트가
 * 통과한다 — 그게 {@code CoachingStartApiTest}·{@code CoachingMessageApiTest} 가 이 규칙을
 * 볼 수 없는 이유다. 대신
 * 실제로 커밋하므로 {@link #cleanUp()} 이 자기 사용자의 행만 지운다.
 *
 * <p>포트는 {@code @MockitoBean} 으로 바꾼다. Gemini 는 프로세스 밖이고, 필요한 것은 "호출
 * 순간의 상태"를 들여다볼 자리다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoachingTransactionBoundaryTest {

    private static final String EMAIL = "coaching-tx@email.com";
    private static final String PASSWORD = "password123!";

    private static final long DELIVERY = 2L;

    @MockitoBean
    CoachingReplyGenerator coachingReplyGenerator;

    @MockitoBean
    CoachingOpeningGenerator coachingOpeningGenerator;

    @Autowired
    NudgeRepository nudgeRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    CoachingMessageRepository coachingMessageRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    private String accessToken;
    private User user;
    private Coaching coaching;

    @BeforeEach
    void setUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(body, "$.data.accessToken");

        user = userRepository.findByEmail(EMAIL).orElseThrow();
        coaching = coachingRepository.save(Coaching.start(user));
        coachingMessageRepository.save(CoachingMessage.ai(coaching, "안녕하세요! 어떤 점이 신경 쓰이세요?"));
    }

    @AfterEach
    void cleanUp() {
        Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, EMAIL);
        jdbcTemplate.update("""
                delete from coaching_messages where coaching_id in (
                    select id from coachings where user_id = ?)
                """, userId);
        jdbcTemplate.update("delete from coachings where user_id = ?", userId);
        jdbcTemplate.update("delete from nudges where user_id = ?", userId);
        jdbcTemplate.update("delete from refresh_token where user_id = ?", userId);
        jdbcTemplate.update("delete from users where id = ?", userId);
    }

    private ResultActions send(String content) throws Exception {
        return mockMvc.perform(post("/coachings/{id}/messages", coaching.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"content": "%s"}
                        """.formatted(content)));
    }

    private int activeConnections() throws Exception {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }

    private long messageCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from coaching_messages where coaching_id = ?", Long.class, coaching.getId());
    }

    @Test
    @DisplayName("위험_구간 2번: AI 를 기다리는 동안 트랜잭션도 DB 커넥션도 쥐고 있지 않다")
    void aiCallHoldsNoConnection() throws Exception {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicInteger connectionsDuringCall = new AtomicInteger(-1);
        AtomicLong messagesDuringCall = new AtomicLong(-1);
        given(coachingReplyGenerator.reply(any())).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            connectionsDuringCall.set(activeConnections());
            // 커넥션 수를 잰 뒤에 조회한다 — 이 조회가 커넥션을 하나 빌린다.
            messagesDuringCall.set(messageCount());
            return new CoachingReply("배달을 줄여 볼까요?", null);
        });

        send("퇴근 후 배달이 잦아요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMessage.content").value("배달을 줄여 볼까요?"))
                .andExpect(jsonPath("$.data.aiMessage.isFallback").value(false));

        assertThat(transactionActive).isFalse();
        assertThat(connectionsDuringCall).hasValue(0);
        // 첫 인사 + 사용자 메시지가 AI 호출 전에 이미 커밋돼 있다
        assertThat(messagesDuringCall).hasValue(2);
        assertThat(messageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("AI 가 실패하면 대체 응답을 별도 트랜잭션에서 만들어 200 으로 내린다")
    void fallbackRunsInTheSecondTransaction() throws Exception {
        given(coachingReplyGenerator.reply(any()))
                .willThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        send("퇴근 후 배달이 잦아요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.id").isNumber())
                .andExpect(jsonPath("$.data.aiMessage.id").isNumber())
                .andExpect(jsonPath("$.data.aiMessage.isFallback").value(true));

        assertThat(messageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("AI 를 기다리는 사이 코칭이 끝나면 답은 저장하되 제안은 반영하지 않는다")
    void endedDuringTheCallKeepsTheAnswerButDropsTheProposal() throws Exception {
        given(coachingReplyGenerator.reply(any())).willAnswer(invocation -> {
            // 다른 탭에서 COACH-01 로 새 코칭을 시작해 이 코칭이 취소된 상황
            jdbcTemplate.update("update coachings set status = 'CANCELED' where id = ?", coaching.getId());
            return new CoachingReply("배달을 주 40,000원으로 줄여 볼까요?",
                    new ProposedGoal(DELIVERY, null, GoalType.AMOUNT, 40_000, null, 7));
        });

        send("퇴근 후 배달이 잦아요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiMessage.content").value("배달을 주 40,000원으로 줄여 볼까요?"))
                .andExpect(jsonPath("$.data.proposal").doesNotExist());

        assertThat(messageCount()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select proposal_goal_type from coachings where id = ?", String.class, coaching.getId()))
                .isNull();
    }

    // --- COACH-01 -----------------------------------------------------------

    private ResultActions start(String body) throws Exception {
        return mockMvc.perform(post("/coachings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Nudge givenNudge() {
        return nudgeRepository.save(Nudge.of(user, categoryRepository.findById(DELIVERY).orElseThrow(),
                Weeks.elapsedWeekOf(KstTime.today()),
                SpendingComparison.of(101_000, List.of(55_000, 62_000, 58_000, 65_000))));
    }

    @Test
    @DisplayName("위험_구간 2번: COACH-01 도 첫 메시지를 기다리는 동안 트랜잭션도 커넥션도 쥐지 않는다")
    void openingCallHoldsNoConnection() throws Exception {
        Nudge nudge = givenNudge();
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicInteger connectionsDuringCall = new AtomicInteger(-1);
        given(coachingOpeningGenerator.generate(any())).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            connectionsDuringCall.set(activeConnections());
            return "배달이 평소보다 많았네요.";
        });

        start("""
                {"nudgeId": %d}
                """.formatted(nudge.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messages[0].content").value("배달이 평소보다 많았네요."));

        assertThat(transactionActive).isFalse();
        assertThat(connectionsDuringCall).hasValue(0);
        // 세션·첫 메시지·넛지 상태가 같은 트랜잭션에서 저장됐다 — 이전 코칭은 취소됐다
        assertThat(jdbcTemplate.queryForObject(
                "select status from nudges where id = ?", String.class, nudge.getId()))
                .isEqualTo("COACHING_STARTED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from coachings where id = ?", String.class, coaching.getId()))
                .isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("COACH-01: 없는 넛지면 첫 메시지를 만들기 전에 404 이고, 진행 중 코칭은 그대로다")
    void unknownNudgeChangesNothing() throws Exception {
        start("""
                {"nudgeId": 999999}
                """)
                .andExpect(status().isNotFound());

        verifyNoInteractions(coachingOpeningGenerator);
        assertThat(jdbcTemplate.queryForObject(
                "select status from coachings where id = ?", String.class, coaching.getId()))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("COACH-01: 첫 메시지를 만드는 사이 넛지가 지워져도 코칭은 시작된다")
    void nudgeDeletedDuringTheCallStillStarts() throws Exception {
        Nudge nudge = givenNudge();
        given(coachingOpeningGenerator.generate(any())).willAnswer(invocation -> {
            // 다른 탭의 TX-05/06 이 판정 조건을 깨 NudgeJudgement 가 카드를 지운 상황
            jdbcTemplate.update("delete from nudges where id = ?", nudge.getId());
            return "배달이 평소보다 많았네요.";
        });

        start("""
                {"nudgeId": %d}
                """.formatted(nudge.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }
}
