package com.back.was.domain.auth;

import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만료 Refresh Token 삭제 스케줄러 (서버_내부_처리 11장 · ERD 2장).
 *
 * <p><b>이 클래스는 {@code @Transactional} 이 아니다.</b> 검증 대상이 바로
 * "{@link ExpiredRefreshTokenCleanupJob#deleteExpired()} 가 <b>자기 트랜잭션</b>을 연다"는
 * 것이라서, 테스트가 트랜잭션을 쥐고 있으면 파생 삭제 쿼리가 그 트랜잭션에 얹혀 통과해
 * 버린다 — {@code @Transactional} 을 빼먹은 구현도 초록색이 된다. 그래서 실제로 커밋하고
 * {@code @AfterEach} 에서 SQL 로 치운다({@code ChallengeReportApiTest} 와 같은 선택).
 *
 * <p>크론은 검증하지 않는다. 00:10 에 발화하는지는 시계를 조작해야 보이고, 그래 봐야
 * 확인되는 건 Spring 의 스케줄링이다 — {@code ChallengeTerminationJob} 과 같은 이유로
 * 작업 메서드를 직접 부른다.
 */
@SpringBootTest
class ExpiredRefreshTokenCleanupJobTest {

    @Autowired
    ExpiredRefreshTokenCleanupJob cleanupJob;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    EntityManager entityManager;

    private User user;

    @BeforeEach
    void givenUser() {
        user = transactionTemplate.execute(status ->
                userRepository.save(User.signUp("홍길동", "token-cleanup@email.com", "irrelevant-hash")));
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from refresh_token").executeUpdate();
            entityManager.createNativeQuery("delete from users").executeUpdate();
        });
    }

    @Test
    @DisplayName("만료된 행만 지우고 살아 있는 세션은 남긴다")
    void deletesOnlyExpiredRows() {
        LocalDateTime now = LocalDateTime.now();
        given("expired-30-days-ago", now.minusDays(30));
        given("expired-one-second-ago", now.minusSeconds(1));
        given("alive-until-tomorrow", now.plusDays(1));

        cleanupJob.deleteExpired();

        assertThat(refreshTokenRepository.findByTokenHash("expired-30-days-ago")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("expired-one-second-ago")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("alive-until-tomorrow")).isPresent();
    }

    /**
     * 기준선은 {@code EXPIRES_AT < 현재 시각}(ERD 2장)이라 아직 1초 남은 행은 살아야 한다.
     * 이 경계가 반대로 깨지면 <b>로그인한 사용자를 로그아웃시키는</b> 쪽의 실수다.
     */
    @Test
    @DisplayName("1초 뒤 만료되는 행은 아직 지우지 않는다")
    void keepsRowsThatExpireInTheFuture() {
        given("expires-in-one-second", LocalDateTime.now().plusSeconds(1));

        cleanupJob.deleteExpired();

        assertThat(refreshTokenRepository.findByTokenHash("expires-in-one-second")).isPresent();
    }

    /** 지울 것이 없는 날이 대부분이다 — 조건 기반이라 그냥 0건으로 끝나야 한다. */
    @Test
    @DisplayName("지울 행이 없으면 아무 일도 일어나지 않는다")
    void doesNothingWhenNothingExpired() {
        given("alive", LocalDateTime.now().plusDays(30));

        cleanupJob.deleteExpired();

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    private void given(String tokenHash, LocalDateTime expiresAt) {
        transactionTemplate.executeWithoutResult(status ->
                refreshTokenRepository.save(RefreshToken.issue(user, tokenHash, expiresAt)));
    }
}
