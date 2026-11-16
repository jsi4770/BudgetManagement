package com.back.was.ai.claude;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** claude.* 설정을 등록한다. 실제 클라이언트({@link ClaudeClient})와 mock 둘 다 이 값을 읽는다. */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {
}
