package com.back.was.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * application.yml 의 claude.* 값.
 * 실제 API 키는 yml 에 쓰지 않고 환경변수 ANTHROPIC_API_KEY 로만 들어온다. (깃허브에 키가 안 올라가게)
 *
 * @param apiKey        Claude API 키
 * @param model         모델 ID. 기본 claude-haiku-5-5
 * @param fallbackModel 429·5xx 때 넘어갈 예비 모델. 없으면 null. {@code effort} 를 받는 모델(4.6 이후)이어야 한다
 * @param baseUrl       API 주소. null 이면 SDK 기본값(https://api.anthropic.com) — 테스트가 로컬 서버로 돌릴 때만 쓴다
 * @param timeout       텍스트 호출 한 번의 최대 시간 (기본 15초 — 도구 호출 대화는 여러 왕복이 이 안에 들어간다).
 *                      <b>재시도·예비 모델 전환을 포함한 전체 상한</b>이다
 * @param imageTimeout  이미지(영수증) 호출 한 번의 최대 시간, 같은 의미 (기본 30초 — 이미지 읽기는 10초를 넘기기도 한다)
 * @param maxRetries    5xx·타임아웃 때 같은 모델로 재시도할 횟수 (429 는 재시도하지 않고 예비 모델로)
 * @param maxTokens     응답 한 번의 최대 토큰. 사고(thinking) 토큰도 여기에 포함된다
 * @param effort        low / medium / high / xhigh / max. 기본 low — 짧은 JSON 을 상한 안에 받아야 해서
 * @param mock          true 면 실제 호출 없이 가짜 응답 (키 없는 팀원·CI 용)
 */
@ConfigurationProperties(prefix = "claude")
public record ClaudeProperties(
        String apiKey,
        String model,
        String fallbackModel,
        String baseUrl,
        Duration timeout,
        Duration imageTimeout,
        Integer maxRetries,
        Integer maxTokens,
        String effort,
        Boolean mock,
        Integer mockDelayMs
) {
    private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    public ClaudeProperties {
        if (model == null || model.isBlank()) model = "claude-haiku-5-5";
        if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(model)) fallbackModel = null;
        if (baseUrl != null && baseUrl.isBlank()) baseUrl = null;
        if (timeout == null) timeout = Duration.ofSeconds(15);
        if (imageTimeout == null) imageTimeout = Duration.ofSeconds(30);
        if (maxRetries == null) maxRetries = 1;
        if (maxTokens == null) maxTokens = 4096;
        effort = effort == null || effort.isBlank() ? "low" : effort.strip().toLowerCase(Locale.ROOT);
        if (!EFFORTS.contains(effort)) {
            // 잘못된 값으로 뜨면 모든 AI 호출이 400 → 503 이 된다. 기동 시점에 알린다
            throw new IllegalArgumentException("claude.effort 는 " + EFFORTS + " 중 하나여야 한다: " + effort);
        }
        if (mock == null) mock = false;
        if (mockDelayMs == null) mockDelayMs = 0;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
