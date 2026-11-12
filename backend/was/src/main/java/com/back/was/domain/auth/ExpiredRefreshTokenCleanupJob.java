package com.back.was.domain.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료된 Refresh Token 행 삭제 (서버_내부_처리 11장 두 번째 줄 · ERD 2장
 * "만료된 행 | 일일 스케줄러에서 {@code EXPIRES_AT < 현재 시각}인 행 삭제").
 *
 * <p>로그아웃과 rotation 은 각자 자기 행을 지우지만, <b>그냥 30일을 방치한 세션</b>은 아무도
 * 지우지 않는다. 그 행들은 이미 인증에 쓸 수 없다({@code AuthService.refresh} 가
 * {@code EXPIRES_AT} 를 직접 확인한다) — 즉 보안 목적이 아니라 테이블이 무한히 자라는 것을
 * 막는 작업이다. 그래서 삭제가 하루 밀려도 아무 영향이 없고, 실패해도 다음 실행이 같은 행을
 * 다시 집는다(조건 기반, "마지막 실행 시각" 같은 상태가 없다 —
 * {@code ChallengeTerminationJob} 과 같은 선택이다).
 *
 * <p><b>{@code @Scheduled} 와 {@code @Transactional} 이 같은 메서드에 있는 것이 중요하다.</b>
 * 파생 삭제 쿼리({@link RefreshTokenRepository#deleteByExpiresAtBefore})는 트랜잭션이 없으면
 * 그대로 터지는데, 스케줄러는 빈의 <b>프록시</b>를 부르므로 이 자리에 있으면 트랜잭션이
 * 걸린다. 반대로 {@code run()} 같은 메서드를 하나 더 두고 거기서 이 메서드를 부르면
 * 자기 호출이라 프록시를 거치지 않아 {@code @Transactional} 이 <b>조용히 무시된다</b> —
 * {@code ChallengeTermination} 이 빈을 둘로 나눈 것과 같은 함정이다. 여기는 반복문이 없어서
 * 한 메서드로 끝낼 수 있다.
 *
 * <p><b>시각은 {@code LocalDateTime.now()} 로 읽는다</b> — {@code KstTime} 이 아니다.
 * 기준 시각은 {@code EXPIRES_AT} 를 <b>쓴 쪽과 같은 시계</b>여야 하고, 그 값은
 * {@code AuthService} 가 {@code LocalDateTime.now().plusSeconds(...)} 로 만든다. 여기만 KST
 * 로 읽으면 JVM 기본 시간대가 KST 가 아닌 서버에서 기준선이 9시간 어긋나 <b>아직 살아 있는
 * 세션을 지운다</b>. 컨테이너는 {@code TZ=Asia/Seoul} 이라 지금은 둘이 같다(Dockerfile);
 * 이 쌍을 KST 로 옮기려면 두 곳을 함께 바꿔야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredRefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 서버_내부_처리 11장의 "00:05 이후". 챌린지 종료 판정(00:05)과 겹치지 않게 00:10 에
     * 돌린다 — 두 작업은 서로 무관하지만 Boot 의 {@code taskScheduler} 는 기본 풀 크기가
     * 1 이라 같은 시각에 두면 한쪽이 상대를 기다린다.
     *
     * <p>{@code zone} 을 명시하는 것은 {@code ChallengeTerminationJob} 과 같은 이유다. 빼면
     * JVM 기본 시간대로 발화해 "00:10 KST" 가 아니게 된다.
     */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpired() {
        long deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        if (deleted > 0) {
            log.info("만료 Refresh Token {}건 삭제", deleted);
        }
    }
}
