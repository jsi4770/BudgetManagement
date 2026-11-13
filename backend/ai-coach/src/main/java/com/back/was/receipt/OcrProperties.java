package com.back.was.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml 의 ocr.* 값.
 *
 * @param djangoUrl      Django OCR 서버의 내부 API 주소 (예: http://ocr-server:8000/ocr). 비어 있으면 미사용
 * @param timeout        OCR 응답 대기 시간
 * @param geminiFallback Django 주소가 없을 때 Gemini 가 이미지를 직접 읽게 할지 (개발·시연 대비)
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(String djangoUrl, Duration timeout, Boolean geminiFallback) {
    public OcrProperties {
        if (timeout == null) timeout = Duration.ofSeconds(20);
        if (geminiFallback == null) geminiFallback = true;
    }

    public boolean hasDjango() {
        return djangoUrl != null && !djangoUrl.isBlank();
    }
}
