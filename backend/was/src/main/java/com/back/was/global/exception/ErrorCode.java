package com.back.was.global.exception;

import org.springframework.http.HttpStatus;

/**
 * API 명세서 0장 4) 공통 에러 코드 — 표에 있는 16개를 표 순서 그대로, HTTP 상태까지
 * 명세값으로 고정한다.
 *
 * <p>명세 전체를 교차 검증한 결과 엔드포인트별 {@code Errors} 줄에 쓰인 코드는 모두
 * 이 16개 안에 있고, 한 코드가 두 개의 HTTP 상태로 쓰이는 경우가 없다. 그래서
 * 상태코드를 enum이 소유할 수 있다. 명세에 없는 코드를 여기 추가하면 프론트와
 * 계약이 깨지므로, 새 코드는 명세 0장 4)를 먼저 고친 뒤 추가한다.
 *
 * <p>{@code message}는 기본 문구다. 명세 예시처럼 필드 단위 문구가 필요하면
 * {@link BusinessException#BusinessException(ErrorCode, String)}으로 덮어쓴다.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 시도해 주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않아요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    CHALLENGE_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 챌린지가 있어요."),
    COACHING_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 코칭이에요."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 용량이 너무 커요."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식이에요."),
    AI_PARSE_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "입력에서 정보를 읽지 못했어요."),
    OCR_FAILED(HttpStatus.BAD_GATEWAY, "영수증 분석에 실패했어요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능을 잠시 사용할 수 없어요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
