package com.back.was.common;

import org.springframework.http.HttpStatus;

/** 403: 기능 자체에 권한이 없음 (예: 시연 계정이 아닌데 시드 생성) */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message, cause);
    }
}
