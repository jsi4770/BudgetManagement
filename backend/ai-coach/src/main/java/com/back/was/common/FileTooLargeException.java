package com.back.was.common;

import org.springframework.http.HttpStatus;

/** 413: 업로드 용량 초과 (영수증 10MB) */
public class FileTooLargeException extends BusinessException {
    public FileTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", message);
    }

    public FileTooLargeException(String message, Throwable cause) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", message, cause);
    }
}
