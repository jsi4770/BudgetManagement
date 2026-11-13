package com.back.was.common;

import org.springframework.http.HttpStatus;

/** 503: Gemini 한도 초과·장애·키 오류 (SNFR-010). 프론트는 직접 입력 화면으로 안내 */
public class AiUnavailableException extends BusinessException {
    public AiUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message, cause);
    }
}
