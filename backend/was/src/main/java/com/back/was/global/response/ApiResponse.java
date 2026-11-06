package com.back.was.global.response;

import com.back.was.global.exception.ErrorResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세서 0장 3) 공통 응답 형식.
 *
 * <p>성공이면 {@code error}, 실패면 {@code data} 키가 직렬화에서 빠진다
 * ({@code NON_NULL}). 명세 예시와 키 구성이 정확히 일치해야 하므로 두 키를
 * 동시에 내보내지 않는다.
 *
 * <p>목록 응답은 {@code ApiResponse<PageResponse<T>>}로 조합한다 — 페이지 정보는
 * 명세상 {@code data} 안에 들어간다.
 *
 * <p>로그아웃(AUTH-04)처럼 {@code 204 No Content}인 엔드포인트는 바디 자체가
 * 없으므로 이 타입을 쓰지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
