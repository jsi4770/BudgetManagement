package com.back.was.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * API 명세서 0장 3) 실패 응답의 {@code error} 본문.
 *
 * <p>{@code details}는 명세상 {@code VALIDATION_ERROR}에만 등장한다. 비어 있으면
 * 키를 아예 내보내지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<Detail> details) {

    /**
     * {@code reason}은 명세에 {@code REQUIRED} 하나만 예시로 있고 전체 목록이 없다.
     * 그래서 닫힌 enum이 아니라 열린 문자열로 둔다 — 명세에 없는 값을 우리가
     * 발명해 계약으로 굳히지 않기 위함이다.
     */
    public record Detail(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, null);
    }

    public static ErrorResponse of(ErrorCode code, String message, List<Detail> details) {
        return new ErrorResponse(code.name(), message, details == null || details.isEmpty() ? null : details);
    }
}
