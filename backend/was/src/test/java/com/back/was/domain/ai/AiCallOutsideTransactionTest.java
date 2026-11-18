package com.back.was.domain.ai;

import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeTermination;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeJudgedEvent;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeTextFiller;
import com.back.was.domain.transaction.TransactionService;
import com.back.was.domain.transaction.dto.ParseTransactionRequest;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * AI 를 부르는 백그라운드·비대화 경로가 <b>트랜잭션 밖에서, DB 커넥션 없이</b> 부르는지
 * (2026-11-15). COACH-01·03 은 {@code CoachingTransactionBoundaryTest} 에 있다.
 *
 * <p>대상은 셋이다 — TX-07 파싱, 후처리 5번 넛지 문구({@link NudgeTextFiller}), 챌린지 종료 판정의
 * 성장 리포트 피드백({@link ChallengeTermination}). 셋 다 예전에는 트랜잭션 안에서 AI 를
 * 기다려서, 호출 상한(10초)만큼 커넥션을 쥐었다. 넛지 문구는 비동기 스레드(기본 8개)마다 그랬으니
 * 거래 등록이 몰리면 풀(기본 10개)이 말랐다.
 *
 * <p><b>이 클래스는 {@code @Transactional} 이 아니다</b> — 테스트 트랜잭션이 있으면 서비스의
 * 트랜잭션이 거기 합류해 경계가 보이지 않는다. 실제로 커밋하므로 {@link #cleanUp()} 이 자기
 * 사용자의 행만 지운다.
 *
 * <p>넛지 문구는 {@code @Async} 라 {@code NudgeTextApiTest} 처럼 동기 실행기로 바꾼다.
 */
@SpringBootTest
class AiCallOutsideTransactionTest {

    @TestConfiguration
    static class SynchronousExecution {

        @Bean
        Executor applicationTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    private static final String EMAIL = "ai-outside-tx@email.com";

    private static final long DELIVERY = 2L;

    @MockitoBean
    TransactionTextParser transactionTextParser;

    @MockitoBean
    NudgeTextGenerator nudgeTextGenerator;

    @MockitoBean
    GrowthFeedbackGenerator growthFeedbackGenerator;

    @Autowired
    TransactionService transactionService;

    @Autowired
    NudgeTextFiller nudgeTextFiller;

    @Autowired
    ChallengeTermination challengeTermination;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    NudgeRepository nudgeRepository;

    @Autowired
    CoachingRepository coachingRepository;

    @Autowired
    ChallengeRepository challengeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    private final AtomicBoolean transactionActive = new AtomicBoolean(true);
    private final AtomicInteger connectionsDuringCall = new AtomicInteger(-1);

    private User user;
    private Category delivery;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.signUp("홍길동", EMAIL, "hash"));
        delivery = categoryRepository.findById(DELIVERY).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        Long userId = user.getId();
        jdbcTemplate.update("""
                delete from growth_reports where challenge_id in (
                    select ch.id from challenges ch
                    join coachings c on ch.coaching_id = c.id
                    where c.user_id = ?)
                """, userId);
        jdbcTemplate.update("""
                delete from challenges where coaching_id in (
                    select id from coachings where user_id = ?)
                """, userId);
        jdbcTemplate.update("delete from coachings where user_id = ?", userId);
        jdbcTemplate.update("delete from nudges where user_id = ?", userId);
        jdbcTemplate.update("delete from users where id = ?", userId);
    }

    /** AI 포트가 불린 순간의 상태를 기록한다. 커넥션 수를 먼저 잰다 — 다른 조회가 하나를 빌리기 전에. */
    private void recordCallState() {
        transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
        try {
            connectionsDuringCall.set(
                    dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertCalledOutsideTransaction() {
        assertThat(transactionActive).as("AI 호출 중 트랜잭션").isFalse();
        assertThat(connectionsDuringCall).as("AI 호출 중 쥔 커넥션").hasValue(0);
    }

    // --- TX-07 --------------------------------------------------------------

    @Test
    @DisplayName("TX-07: 파싱 중에 트랜잭션도 커넥션도 쥐지 않는다")
    void parseCallsTheParserOutsideATransaction() {
        given(transactionTextParser.parse(any())).willAnswer(invocation -> {
            recordCallState();
            return new ParsedTransaction(12_000, KstTime.today(), DELIVERY, "치킨");
        });

        transactionService.parse(new ParseTransactionRequest("어제 치킨 12000원"));

        assertCalledOutsideTransaction();
    }

    // --- 후처리 5번: 넛지 문구 -------------------------------------------------

    private Nudge givenNudge() {
        return nudgeRepository.save(Nudge.of(user, delivery, Weeks.elapsedWeekOf(KstTime.today()),
                SpendingComparison.of(101_000, List.of(55_000, 62_000, 58_000, 65_000))));
    }

    private String storedAnalysis(Nudge nudge) {
        return jdbcTemplate.queryForObject(
                "select ai_analysis from nudges where id = ?", String.class, nudge.getId());
    }

    @Test
    @DisplayName("후처리 5번: 넛지 문구를 만드는 동안 트랜잭션도 커넥션도 쥐지 않고, 끝나면 저장한다")
    void nudgeTextIsGeneratedOutsideATransaction() {
        Nudge nudge = givenNudge();
        given(nudgeTextGenerator.generate(any())).willAnswer(invocation -> {
            recordCallState();
            return new NudgeText("배달이 평소보다 많아요", "이번 주는 집밥 어때요?");
        });

        nudgeTextFiller.fill(new NudgeJudgedEvent(nudge.getId()));

        assertCalledOutsideTransaction();
        assertThat(storedAnalysis(nudge)).isEqualTo("배달이 평소보다 많아요");
    }

    @Test
    @DisplayName("후처리 5번: 문구를 만드는 사이 넛지 금액이 갱신되면 옛 숫자로 만든 문구는 저장하지 않는다")
    void staleNudgeTextIsDropped() {
        Nudge nudge = givenNudge();
        given(nudgeTextGenerator.generate(any())).willAnswer(invocation -> {
            // 다른 거래 등록이 재판정해 금액을 바꾼 상황 — 그 판정이 쏜 이벤트가 새 문구를 만든다
            jdbcTemplate.update("update nudges set spending_amount = 150000 where id = ?", nudge.getId());
            return new NudgeText("배달 101,000원", "옛 숫자");
        });

        nudgeTextFiller.fill(new NudgeJudgedEvent(nudge.getId()));

        assertThat(storedAnalysis(nudge)).isNull();
    }

    // --- 챌린지 종료 판정: 성장 리포트 피드백 ------------------------------------------

    /** 지난주에 끝난 배달 40,000원 목표, 실적 0원 — 그대로 판정하면 SUCCESS. */
    private Challenge givenEndedChallenge() {
        Coaching coaching = coachingRepository.save(Coaching.start(user));
        LocalDate start = KstTime.today().minusDays(10);
        return challengeRepository.save(Challenge.of(coaching, delivery, "배달 줄이기",
                GoalType.AMOUNT, 40_000, null, start, start.plusDays(6)));
    }

    private String storedFeedback(Challenge challenge) {
        return jdbcTemplate.queryForObject(
                "select feedback from growth_reports where challenge_id = ?", String.class, challenge.getId());
    }

    @Test
    @DisplayName("종료 판정: 피드백을 만드는 동안 트랜잭션도 커넥션도 쥐지 않고, 판정과 리포트는 함께 저장된다")
    void growthFeedbackIsGeneratedOutsideATransaction() {
        Challenge challenge = givenEndedChallenge();
        given(growthFeedbackGenerator.generate(any())).willAnswer(invocation -> {
            recordCallState();
            return "목표를 지켰어요";
        });

        challengeTermination.terminate(challenge.getId());

        assertCalledOutsideTransaction();
        assertThat(storedFeedback(challenge)).isEqualTo("목표를 지켰어요");
        assertThat(jdbcTemplate.queryForObject(
                "select status from challenges where id = ?", String.class, challenge.getId()))
                .isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("종료 판정: 피드백을 만드는 사이 실적이 바뀌어 판정이 뒤집히면 문구를 다시 만든다")
    void feedbackIsRegeneratedWhenTheVerdictChanges() {
        Challenge challenge = givenEndedChallenge();
        AtomicInteger calls = new AtomicInteger();
        given(growthFeedbackGenerator.generate(any())).willAnswer(invocation -> {
            GrowthFeedbackCommand command = invocation.getArgument(0);
            if (calls.incrementAndGet() == 1) {
                // 종료일이 지난 날짜로 거래가 들어와 후처리 2번이 실적을 올린 상황
                jdbcTemplate.update("update challenges set spending_amount = 90000 where id = ?",
                        challenge.getId());
            }
            return command.success() ? "목표를 지켰어요" : "아쉽게 넘었어요";
        });

        challengeTermination.terminate(challenge.getId());

        assertThat(calls).hasValue(2);
        assertThat(storedFeedback(challenge)).isEqualTo("아쉽게 넘었어요");
        assertThat(jdbcTemplate.queryForObject(
                "select status from challenges where id = ?", String.class, challenge.getId()))
                .isEqualTo("FAILED");
    }
}
