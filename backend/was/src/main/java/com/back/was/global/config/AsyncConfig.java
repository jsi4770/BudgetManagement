package com.back.was.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 활성화. 지금 쓰는 곳은 넛지 AI 문구 생성
 * ({@code NudgeTextFiller}) 하나다 — 서버_내부_처리 8장이 요구한
 * "커밋 후 비동기".
 *
 * <p>실행기는 Boot 가 자동 구성한 {@code applicationTaskExecutor} 를 쓴다. 전용 풀을 따로
 * 두지 않는 이유: 비동기 작업이 이 하나뿐이고, 풀 크기·큐 길이는 설정
 * ({@code spring.task.execution.*})으로 조절할 수 있다.
 *
 * <p>테스트에서 이 비동기 구간을 결정적으로 돌려야 하면 {@code Executor} 빈을 하나
 * 정의하면 된다 — Boot 의 자동 구성이 {@code @ConditionalOnMissingBean(Executor.class)}
 * 라서 물러난다 ({@code NudgeTextApiTest} 가 그렇게 동기 실행기로 바꿔 끼운다).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
