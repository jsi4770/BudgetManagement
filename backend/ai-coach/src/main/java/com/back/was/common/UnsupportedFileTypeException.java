package com.back.was.common;

import org.springframework.http.HttpStatus;

/** 415: 지원하지 않는 이미지 형식 (jpg/png만) */
public class UnsupportedFileTypeException extends BusinessException {
    public UnsupportedFileTypeException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message);
    }

    public UnsupportedFileTypeException(String message, Throwable cause) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message, cause);
    }
}
