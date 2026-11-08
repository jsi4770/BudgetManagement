package com.back.was.domain.analysis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * ANL-02 쿼리 파라미터. 규칙은 명세 ANL-02 표 그대로 — {@code weeks} 는 선택이고 기본값
 * 4(이번 주 포함)다.
 *
 * <p>파라미터가 하나뿐인데 레코드로 묶은 이유는 {@code TransactionSearchRequest} 와 같다:
 * 제약을 핸들러 파라미터에 직접 걸면 Spring 7 의 메서드 검증이
 * {@code HandlerMethodValidationException} 을 던지고 전역 핸들러에 매핑이 없어 500 이 된다.
 * {@code @Valid @ModelAttribute} 레코드면 {@code BindException} → 400
 * {@code VALIDATION_ERROR} + {@code details} 다.
 */
public record WeeklyTrendRequest(

        /**
         * 막대 개수(이번 주 포함). 기본값 4.
         *
         * <p><b>상한 52 는 명세에 없는 이 구현의 결정이다.</b> 명세는 범위를 정하지 않았지만
         * 상한이 없으면 {@code weeks=100000} 하나로 10년치 집계 범위가 열린다. 1년(52주)이면
         * 어떤 화면보다 길고, TX-02 의 {@code size} 와 같이 <b>조용히 깎지 않고 400 으로
         * 거부</b>한다 — 요청한 개수와 받은 개수가 다르면 차트의 x축이 어긋나고 응답만
         * 봐서는 드러나지 않는다.
         */
        @Min(1)
        @Max(52)
        Integer weeks
) {
    /** 명세 ANL-02: 기본값 4 (이번 주 포함). */
    private static final int DEFAULT_WEEKS = 4;

    public int weeksOrDefault() {
        return weeks != null ? weeks : DEFAULT_WEEKS;
    }
}
