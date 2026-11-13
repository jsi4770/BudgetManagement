package com.back.was.common;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/** 400 VALIDATION_ERROR : 필수값 누락, 형식 오류. details 에 field/reason 을 담는다 */
public class InvalidInputException extends BusinessException {
    public InvalidInputException(String message, String field, String reason) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(Map.of("field", field, "reason", reason)), null);
    }
}
