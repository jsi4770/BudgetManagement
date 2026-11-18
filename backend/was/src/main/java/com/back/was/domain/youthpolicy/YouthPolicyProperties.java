package com.back.was.domain.youthpolicy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code youth-policy.api.*}. 인증키는 환경변수 {@code YOUTH_POLICY_API_KEY} 로만 들어온다.
 *
 * @param baseUrl 온통청년 청년정책 API 주소 (기본 {@code https://www.youthcenter.go.kr/go/ythip/getPlcy})
 * @param key     인증키. 비어 있으면 동기화만 건너뛴다
 * @param pageSize 한 번에 받는 건수(확인된 최대 100)
 * @param timeout 호출 한 번의 읽기 대기 시간
 */
@ConfigurationProperties(prefix = "youth-policy.api")
public record YouthPolicyProperties(String baseUrl, String key, Integer pageSize, Duration timeout) {

    public YouthPolicyProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://www.youthcenter.go.kr/go/ythip/getPlcy";
        if (key == null) key = "";
        if (pageSize == null || pageSize < 1) pageSize = 100;
        if (timeout == null) timeout = Duration.ofSeconds(30);
    }

    public boolean hasKey() {
        return !key.isBlank();
    }

    @Override
    public String toString() {
        return "YouthPolicyProperties[baseUrl=" + baseUrl + ", key=" + (hasKey() ? "****" : "(none)") + "]";
    }
}
