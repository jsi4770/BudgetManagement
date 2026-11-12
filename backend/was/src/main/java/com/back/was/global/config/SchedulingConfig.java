package com.back.was.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화. 지금 쓰는 작업은 만료 Refresh Token 삭제 하나다
 * ({@code ExpiredRefreshTokenCleanupJob}, 00:10 KST). 서버_내부_처리 11장의 다른 하나 —
 * 챌린지 종료 판정(00:05 KST) — 는 챌린지 도메인이 미구현이라 아직 없다.
 *
 * <p>실행기는 Boot 의 {@code taskScheduler} 를 쓴다. <b>기본 풀 크기가 1</b>이라 작업들이
 * 순서대로 돌고, 그래서 11장이 두 크론을 5분 떨어뜨려 뒀다 — 종료 판정을 붙일 때
 * <b>00:10 을 비워 둘 것</b>. 풀이 필요해지면 {@code spring.task.scheduling.*} 로 키운다.
 *
 * <p><b>{@code @EnableAsync} 를 여기에 합치지 말 것.</b> 두 애너테이션은 독립이고 꺼야 할
 * 상황도 다르다 — 비동기를 끄면 넛지 문구만 멈추지만 스케줄링을 끄면 챌린지가 영원히
 * {@code IN_PROGRESS} 로 남는다. 비동기를 켤 때 밟게 되는 함정(실행기를 이름으로 못박지
 * 않으면 비동기 작업이 이 스케줄러의 풀로 이주한다)은
 * {@code docs/AI_코칭_소비분석_인수인계.md} 10장에 적어 뒀다.
 *
 * <p>테스트에서는 크론을 기다리지 않고 작업 빈의 메서드를 직접 부른다. 스케줄링이 테스트
 * 컨텍스트에서도 켜지지만 00:10 KST 에만 발화하므로 실제로 끼어들 창이 거의 없다 — 정확히
 * 그 시각에 테스트를 돌리면 겹칠 수 있다는 점은 받아들인 위험이다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
