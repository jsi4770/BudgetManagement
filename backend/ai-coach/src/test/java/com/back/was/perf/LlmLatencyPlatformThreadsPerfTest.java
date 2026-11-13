package com.back.was.perf;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gemini.mock=true", "gemini.mock-delay-ms=400", "spring.threads.virtual.enabled=false"})
class LlmLatencyPlatformThreadsPerfTest extends LlmLatencyBase {
    @Override String label() { return "platform threads"; }
}
