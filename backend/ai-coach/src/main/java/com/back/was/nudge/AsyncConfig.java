package com.back.was.nudge;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 활성화 (넛지 AI 문구 비동기 생성). 팀에 이미 @EnableAsync 가 있으면 이 파일은 지워도 됨 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
