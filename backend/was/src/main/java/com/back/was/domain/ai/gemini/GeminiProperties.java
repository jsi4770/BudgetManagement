package com.back.was.domain.ai.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml 의 gemini.* 값.
 * 실제 API 키는 yml 에 쓰지 않고 환경변수 GEMINI_API_KEY 로만 들어온다. (깃허브에 키가 안 올라가게)
 *
 * @param apiKey     Gemini API 키
 * @param model      모델명. 기본 gemini-flash-latest (최신 Flash 자동 연결 별칭)
 * @param baseUrl    Gemini REST 주소
 * @param timeout    텍스트 호출의 응답 대기 최대 시간 (기본 10초 — 서버_내부_처리 8장)
 * @param imageTimeout 이미지(영수증) 호출의 응답 대기 최대 시간 (기본 30초 — 이미지 읽기는 10초를 넘기기도 한다)
 * @param maxRetries 429(한도 초과)·5xx(장애)·타임아웃 때 재시도 횟수
 * @param mock       true 면 실제 호출 없이 가짜 응답 (키 없는 팀원·CI 용)
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String fallbackModel,
        String baseUrl,
        Duration timeout,
        Duration imageTimeout,
        Integer maxRetries,
        Boolean mock,
        Integer mockDelayMs
) {
    public GeminiProperties {
        if (model == null || model.isBlank()) model = "gemini-flash-latest";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        if (timeout == null) timeout = Duration.ofSeconds(10);   // 서버_내부_처리 8장: TX-07 타임아웃 10초
        if (imageTimeout == null) imageTimeout = Duration.ofSeconds(30);
        if (maxRetries == null) maxRetries = 1;
        if (mock == null) mock = false;
        if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(model)) fallbackModel = null;
        if (mockDelayMs == null) mockDelayMs = 0;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
