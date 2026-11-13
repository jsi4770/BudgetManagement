package com.back.was.domain.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code ocr.*} 설정. 환경변수로는 {@code OCR_BASE_URL}, {@code OCR_TIMEOUT}.
 *
 * @param baseUrl Django OCR 서버 주소 (예: {@code http://localhost:8000}). <b>비어 있으면 Django 를 쓰지 않고</b>
 *                Gemini 가 이미지를 직접 읽는다
 * @param timeout OCR 서버 응답 대기 시간 (기본 20초 — 멀티모달 호출이라 TX-07 의 10초보다 넉넉히)
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(String baseUrl, Duration timeout) {

    public OcrProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
    }
}
