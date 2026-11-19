package com.back.was.common;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * 명세 "공통 에러 코드"를 표현하는 예외의 부모.
 * HTTP 상태 + 에러 코드(code) + 메시지 + details 를 들고 다니고,
 * BusinessExceptionHandler 가 명세 실패 응답 형식으로 바꿔 준다.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<Map<String, String>> details;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of(), null);
    }

    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        this(status, code, message, List.of(), cause);
    }

    public BusinessException(HttpStatus status, String code, String message,
                             List<Map<String, String>> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : details;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<Map<String, String>> getDetails() { return details; }
}
