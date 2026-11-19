package com.back.was.common;

import org.springframework.http.HttpStatus;

/** 422: AI가 입력에서 정보를 추출하지 못함 */
public class AiParseFailedException extends BusinessException {
    public AiParseFailedException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "AI_PARSE_FAILED", message);
    }

    public AiParseFailedException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "AI_PARSE_FAILED", message, cause);
    }
}
