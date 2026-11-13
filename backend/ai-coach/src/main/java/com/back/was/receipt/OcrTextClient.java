package com.back.was.receipt;

/** 영수증 이미지 → 글자(텍스트). 명세상 Django OCR 서버(CLOVA)가 담당 */
public interface OcrTextClient {

    /** 연결 설정이 되어 있는지 */
    boolean isConfigured();

    /** 실패하면 OcrFailedException(502) */
    String extractText(byte[] image, String mimeType);
}
