package com.back.was.domain.analysis;

import com.back.was.domain.analysis.dto.CategoryComparisonRequest;
import com.back.was.domain.analysis.dto.CategoryComparisonResponse;
import com.back.was.domain.analysis.dto.MonthlySummaryResponse;
import com.back.was.domain.analysis.dto.WeeklyTrendRequest;
import com.back.was.domain.analysis.dto.WeeklyTrendResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import com.back.was.global.support.YearMonths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 명세 8장 소비 분석(ANALYSIS). 대상은 항상 토큰의 주인이라 경로에 사용자 ID 가 없다. */
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * ANL-01 월별 요약.
     *
     * <p>{@code yearMonth} 는 선택이고 기본값은 이번 달(KST)이다. 제약 애노테이션을 붙이지
     * 않고 {@link YearMonths#parseOrCurrent} 로 직접 검증하는 이유는 CLAUDE.md 에 적힌
     * 그대로다 — 핸들러 파라미터에 제약을 걸면 Spring 7 의 메서드 검증이
     * {@code HandlerMethodValidationException} 을 던지고, 전역 핸들러가 그걸 매핑하지 않아
     * 500 이 된다. 직접 파싱하면 400 {@code VALIDATION_ERROR} + 어떤 값이 왜 틀렸는지가
     * 메시지에 담긴다.
     */
    @GetMapping("/monthly")
    public ApiResponse<MonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String yearMonth) {
        return ApiResponse.ok(analysisService.getMonthlySummary(
                authUser.id(), YearMonths.parseOrCurrent(yearMonth)));
    }

    /**
     * ANL-02 주간 지출 추이.
     *
     * <p>{@code weeks} 에는 범위 제약이 있어 {@code yearMonth} 와 달리 {@code @Valid
     * @ModelAttribute} 레코드로 받는다 — 제약을 파라미터에 직접 걸면 500 이 된다
     * ({@link WeeklyTrendRequest} 설명).
     */
    @GetMapping("/weekly")
    public ApiResponse<WeeklyTrendResponse> getWeeklyTrend(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute WeeklyTrendRequest request) {
        return ApiResponse.ok(analysisService.getWeeklyTrend(authUser.id(), request));
    }

    /**
     * ANL-03 카테고리별 평소 대비 비교.
     *
     * <p>{@code period}·{@code baseDate} 에 제약 애노테이션은 없지만 레코드로 받는다 —
     * 잘못된 열거형 값이나 날짜 형식이 {@code BindException} 이 되어 어느 필드가 틀렸는지
     * {@code details} 에 담기기 때문이다 ({@link CategoryComparisonRequest} 설명).
     */
    @GetMapping("/categories")
    public ApiResponse<CategoryComparisonResponse> getCategoryComparison(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute CategoryComparisonRequest request) {
        return ApiResponse.ok(analysisService.getCategoryComparison(authUser.id(), request));
    }
}
