package com.back.was.domain.merchant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.properties 의 {@code merchant.api.*}. 서비스키는 환경변수 {@code MERCHANT_API_KEY} 로만
 * 들어온다 — 코드·저장소에는 두지 않는다.
 *
 * @param baseUrl 가맹점 API 주소 (오퍼레이션 {@code /franchiseV3} 앞까지)
 * @param key     공공데이터포털 서비스키. 비어 있으면 동기화만 건너뛴다
 * @param perPage 동기화 때 한 번에 받는 건수
 * @param timeout 호출 한 번의 읽기 대기 시간
 */
@ConfigurationProperties(prefix = "merchant.api")
public record MerchantProperties(String baseUrl, String key, Integer perPage, Duration timeout) {

    public MerchantProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/B190001/localFranchisesV3";
        }
        if (key == null) key = "";
        if (perPage == null || perPage < 1) perPage = 1000;
        if (timeout == null) timeout = Duration.ofSeconds(30);
    }

    public boolean hasKey() {
        return !key.isBlank();
    }

    /** 로그·예외에 이 객체가 찍혀도 키가 새지 않게 한다. */
    @Override
    public String toString() {
        return "MerchantProperties[baseUrl=" + baseUrl + ", key=" + (hasKey() ? "****" : "(none)") + "]";
    }
}
