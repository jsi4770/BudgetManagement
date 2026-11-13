package com.back.was.domain.ai.gemini;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gemini 호출 실패를 팀 공통 에러 코드({@link ErrorCode})로 바꾼다.
 *
 * <p>응답 문구는 코드의 기본 메시지를 쓰고(명세 0장 4) · 프론트 안내와 일치), 원인 설명은
 * 로그에만 남긴다. <b>사용자 입력·이미지 원문은 로그에 넣지 않는다</b> — 여기로 넘어오는
 * {@code detail} 은 "AI 서비스 설정이 없다" 같은 상황 설명이어야 한다.
 */
public final class GeminiErrors {

    private static final Logger log = LoggerFactory.getLogger(GeminiErrors.class);

    private GeminiErrors() {
    }

    /** 503 {@code AI_UNAVAILABLE} — 한도 초과·장애·타임아웃·키 오류. */
    public static BusinessException unavailable(String detail) {
        log.warn("[Gemini] {}", detail);
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    public static BusinessException unavailable(String detail, Throwable cause) {
        log.warn("[Gemini] {} ({})", detail, cause.toString());
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    /** 422 {@code AI_PARSE_FAILED} — 응답은 왔지만 읽을 수 없거나 비어 있음. */
    public static BusinessException parseFailed(String detail) {
        log.warn("[Gemini] {}", detail);
        return new BusinessException(ErrorCode.AI_PARSE_FAILED);
    }

    public static BusinessException parseFailed(String detail, Throwable cause) {
        log.warn("[Gemini] {} ({})", detail, cause.toString());
        return new BusinessException(ErrorCode.AI_PARSE_FAILED);
    }
}
