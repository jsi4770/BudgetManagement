package com.back.was.global.exception;

/**
 * 도메인 규칙 위반을 {@link ErrorCode}로 표현하는 예외.
 * {@link GlobalExceptionHandler}가 코드·상태·메시지를 그대로 응답으로 옮긴다.
 *
 * <p>소유권 위반은 {@code FORBIDDEN}이 아니라 {@code NOT_FOUND}로 던진다 —
 * 명세 0장 5) 참고. {@code FORBIDDEN}은 관리자 API 권한 부족 전용이다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 명세 0장 5): 리소스 없음과 남의 리소스를 구분하지 않고 404로 내린다. */
    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }
}
