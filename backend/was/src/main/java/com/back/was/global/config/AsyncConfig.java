package com.back.was.global.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * {@code @Async} 활성화. 지금 쓰는 곳은 넛지 AI 문구 생성
 * ({@code NudgeTextFiller}) 하나다 — 서버_내부_처리 8장이 요구한
 * "커밋 후 비동기".
 *
 * <p>실행기는 Boot 가 자동 구성한 {@code applicationTaskExecutor} 를 쓴다. 전용 풀을 따로
 * 두지 않는 이유: 비동기 작업이 이 하나뿐이고, 풀 크기·큐 길이는 설정
 * ({@code spring.task.execution.*})으로 조절할 수 있다.
 *
 * <p><b>실행기를 이름으로 못박는 것이 이 클래스의 핵심이다.</b> {@link AsyncConfigurer} 를
 * 구현하지 않으면 Spring 이 "유일한 {@code TaskExecutor} 빈"을 찾아 쓰는데,
 * {@link SchedulingConfig} 가 만드는 {@code taskScheduler} 도 {@code TaskExecutor} 다 —
 * 그래서 스케줄링을 켜는 순간 비동기 작업이 <b>스케줄러의 스레드 풀</b>(기본 크기 1)로
 * 조용히 옮겨 간다. 실제로 그렇게 깨졌다(2026-11-10): {@code NudgeTextApiTest} 가 심어 둔
 * 동기 실행기가 무시되면서 테스트가 레이스로 실패했다.
 *
 * <p>그래서 이름으로 꺼낸다. 테스트가 {@code applicationTaskExecutor} 라는 <b>같은
 * 이름</b>으로 빈을 정의하면 그쪽이 쓰이고({@code NudgeTextApiTest} 가 동기 실행기로 바꿔
 * 끼우는 방법), 운영에서는 Boot 의 자동 구성 빈이 쓰인다. {@code BeanFactory} 로 늦게
 * 꺼내는 건 설정 클래스가 실행기보다 먼저 만들어질 수 있기 때문이다.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** Boot 의 {@code TaskExecutionAutoConfiguration} 이 만드는 빈 이름. */
    private static final String EXECUTOR_BEAN_NAME = "applicationTaskExecutor";

    private final BeanFactory beanFactory;

    AsyncConfig(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Executor getAsyncExecutor() {
        return beanFactory.getBean(EXECUTOR_BEAN_NAME, Executor.class);
    }
}
