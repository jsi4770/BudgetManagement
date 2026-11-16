package com.back.was.ai.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.properties 의 claude.* 값.
 * 실제 API 키는 파일에 쓰지 않고 환경변수 ANTHROPIC_API_KEY 로만 들어온다.
 *
 * @param apiKey        Claude API 키
 * @param model         모델 ID (기본 claude-opus-5-5)
 * @param fallbackModel 기본 모델이 429·장애일 때 넘어갈 예비 모델. 비우거나 model 과 같으면 사용 안 함
 * @param baseUrl       API 주소. 테스트가 가짜 서버로 돌릴 때만 바꾼다
 * @param effort        생각 깊이(low|medium|high|xhigh|max). 응답 시간 상한이 10초라 기본 low
 * @param maxTokens     응답 한 번의 출력 토큰 상한 (생각 토큰 포함)
 * @param timeout       텍스트 호출 한 번의 최대 시간 (기본 10초 — 서버_내부_처리 8장). <b>재시도·예비 모델 전환을
 *                      포함한 전체 상한</b>이다
 * @param imageTimeout  이미지(영수증) 호출 한 번의 최대 시간, 같은 의미 (기본 30초)
 * @param maxRetries    429·5xx·타임아웃 때 같은 모델 재시도 횟수
 * @param mock          true 면 실제 호출 없이 가짜 응답 (키 없는 팀원·CI 용)
 * @param mockDelayMs   mock 응답 인위적 지연 (성능 테스트용)
 */
@ConfigurationProperties(prefix = "claude")
public record ClaudeProperties(
        String apiKey,
        String model,
        String fallbackModel,
        String baseUrl,
        String effort,
        Long maxTokens,
        Duration timeout,
        Duration imageTimeout,
        Integer maxRetries,
        Boolean mock,
        Integer mockDelayMs
) {
    public ClaudeProperties {
        if (model == null || model.isBlank()) model = "claude-opus-5-5";
        if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(model)) fallbackModel = null;
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.anthropic.com";
        if (effort == null || effort.isBlank()) effort = "low";
        if (maxTokens == null) maxTokens = 16_000L;
        if (timeout == null) timeout = Duration.ofSeconds(10);   // 서버_내부_처리 8장: TX-07 타임아웃 10초
        if (imageTimeout == null) imageTimeout = Duration.ofSeconds(30);
        if (maxRetries == null) maxRetries = 1;
        if (mock == null) mock = false;
        if (mockDelayMs == null) mockDelayMs = 0;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
