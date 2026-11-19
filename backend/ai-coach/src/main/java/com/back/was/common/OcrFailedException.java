package com.back.was.common;

import org.springframework.http.HttpStatus;

/** 502: OCR 서버(Django) 또는 CLOVA OCR 처리 실패 */
public class OcrFailedException extends BusinessException {
    public OcrFailedException(String message) {
        super(HttpStatus.BAD_GATEWAY, "OCR_FAILED", message);
    }

    public OcrFailedException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "OCR_FAILED", message, cause);
    }
}
