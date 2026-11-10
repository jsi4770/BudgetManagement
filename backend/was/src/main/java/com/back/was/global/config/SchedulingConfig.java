package com.back.was.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화. 지금 쓰는 곳은 챌린지 종료 판정
 * ({@code ChallengeTerminationJob}) 하나다 — 서버_내부_처리 11장의 "00:05 KST".
 *
 * <p>{@code AsyncConfig} 와 나눠 둔 이유: 두 애너테이션은 서로 독립이고 꺼야 할 상황도
 * 다르다. 비동기를 끄면 넛지 문구만 멈추지만 스케줄링을 끄면 챌린지가 영원히
 * {@code IN_PROGRESS} 로 남는다.
 *
 * <p>실행기는 Boot 의 {@code taskScheduler} 를 쓴다. 작업이 하나라 전용 풀이 필요 없고,
 * 크기는 {@code spring.task.scheduling.*} 로 조절할 수 있다.
 *
 * <p><b>11장의 두 번째 작업(만료 Refresh Token 삭제)은 아직 없다.</b>
 * {@code RefreshTokenRepository.deleteByExpiresAtBefore} 가 그 자리를 기다리고 있다
 * (`docs/위험_구간.md` 5장).
 *
 * <p>테스트에서는 크론을 기다리지 않고 작업 빈의 메서드를 직접 부른다. 스케줄링이 테스트
 * 컨텍스트에서도 켜지지만 00:05 KST 에만 발화하므로 실제로 끼어들 창이 거의 없다 — 정확히
 * 그 시각에 테스트를 돌리면 겹칠 수 있다는 점은 받아들인 위험이다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
