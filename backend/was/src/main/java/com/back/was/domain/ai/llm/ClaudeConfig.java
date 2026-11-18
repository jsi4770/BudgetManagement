package com.back.was.domain.ai.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * claude.* 설정을 등록한다. SDK 클라이언트는 빈으로 만들지 않는다 — 키가 없을 때 만들지 않아야 해서
 * {@link ClaudeLlmClient} 가 직접 만든다.
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {
}
