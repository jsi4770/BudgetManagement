package com.back.was.domain.analysis.dto;

import com.back.was.domain.analysis.AnalysisPeriod;
import com.back.was.global.support.KstTime;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * ANL-03 쿼리 파라미터. 규칙은 명세 ANL-03 표 그대로 — 둘 다 선택이고 기본값은
 * {@code WEEK} · 오늘(KST)이다.
 *
 * <p>{@code @ModelAttribute} 레코드로 받는 이유는 {@code TransactionSearchRequest} 와 같다.
 * 제약 애노테이션은 없지만 <b>타입 변환 실패도 여기서는 {@code BindException}</b> 이라
 * 400 {@code VALIDATION_ERROR} + {@code details} 가 된다 — 핸들러 파라미터로 받으면
 * {@code MethodArgumentTypeMismatchException} 이 나고 전역 핸들러가 {@code details} 없이
 * 응답한다. {@code period=YEAR} 나 {@code baseDate=어제} 같은 값이 어느 필드 때문인지
 * 프론트에 전달되는 쪽이 낫다.
 *
 * <p><b>미래 {@code baseDate} 는 막지 않는다.</b> 명세에 규정이 없고, TX-02 의
 * {@code from}/{@code to} 도 같은 선택이다. 미래 날짜로 조회하면 아직 거래가 있을 수 없는
 * 날들이 분석 기간에 들어가 이번 기간 금액이 낮게 나오지만, 그건 호출자가 요청한 기간의
 * 정확한 답이다.
 */
public record CategoryComparisonRequest(

        /** 기본값 {@code WEEK}. 열거형에 없는 값은 바인딩 실패 → 400. */
        AnalysisPeriod period,

        /** 기본값 오늘 (KST). */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate baseDate
) {
    private static final AnalysisPeriod DEFAULT_PERIOD = AnalysisPeriod.WEEK;

    public AnalysisPeriod periodOrDefault() {
        return period != null ? period : DEFAULT_PERIOD;
    }

    public LocalDate baseDateOrDefault() {
        return baseDate != null ? baseDate : KstTime.today();
    }
}
