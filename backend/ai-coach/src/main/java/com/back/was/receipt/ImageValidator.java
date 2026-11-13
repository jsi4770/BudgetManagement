package com.back.was.receipt;

import com.back.was.common.FileTooLargeException;
import com.back.was.common.UnsupportedFileTypeException;

/**
 * 영수증 이미지 검증 (RCPT-01): jpg/png 만, 10MB 이하.
 * 확장자는 속일 수 있어서 파일 앞 바이트(매직 넘버)로 실제 형식을 확인한다.
 * HEIC 는 프론트에서 JPG 로 변환해서 보내야 하고, 미변환이면 415.
 */
public final class ImageValidator {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private ImageValidator() {}

    /** @return "image/jpeg" 또는 "image/png" */
    public static String validateAndDetectMime(byte[] data) {
        if (data.length > MAX_BYTES) {
            throw new FileTooLargeException("이미지는 10MB 이하만 올릴 수 있어요.");
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        throw new UnsupportedFileTypeException("jpg 또는 png 이미지만 지원해요.");
    }
}
