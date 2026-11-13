package com.back.was.perf;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gemini.mock=true", "gemini.mock-delay-ms=400", "spring.threads.virtual.enabled=true"})
class LlmLatencyVirtualThreadsPerfTest extends LlmLatencyBase {
    @Override String label() { return "virtual threads"; }
}
