package com.back.was.domain.analysis;

import com.back.was.domain.analysis.dto.CategoryComparisonRequest;
import com.back.was.domain.analysis.dto.CategoryComparisonResponse;
import com.back.was.domain.analysis.dto.MonthlySummaryResponse;
import com.back.was.domain.analysis.dto.WeeklyTrendRequest;
import com.back.was.domain.analysis.dto.WeeklyTrendResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import com.back.was.global.support.YearMonths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "8. 소비 분석 (ANL)", description = """
        홈 요약·주간 추이·카테고리별 비교. 모두 조회 시점에 거래를 집계하므로 따로 저장되는 값이 없습니다.

        "이번 주"는 **월요일 시작**이고 "오늘"은 KST 기준입니다.""")
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 제약 애노테이션을 붙이지
     * 않고 {@link YearMonths#parseOrCurrent} 로 직접 검증하는 이유는 CLAUDE.md 에 적힌
     * 그대로다 — 핸들러 파라미터에 제약을 걸면 Spring 7 의 메서드 검증이
     * {@code HandlerMethodValidationException} 을 던지고, 전역 핸들러가 그걸 매핑하지 않아
     * 500 이 된다. 직접 파싱하면 400 {@code VALIDATION_ERROR} + 어떤 값이 왜 틀렸는지가
     * 메시지에 담긴다.
     */
    @Operation(summary = "ANL-01 월별 요약", description = """
            홈 화면 상단. `yearMonth` 는 선택이고 없으면 이번 달(KST)입니다 —
            단, `?yearMonth=` 처럼 빈 값을 보내면 400 입니다.

            예산이 없는 달은 `budget`·`remainingBudget` 이 `null` 이지만 `budgetUsageRate` 는 `0.00` 입니다
            (진행 바가 NaN 이 되지 않게). `budget` 이 `null` 인지 `0` 인지로 "설정 안 함"과 "0 으로 설정"을 구분하세요.

            거래가 없는 달도 200 이고 전부 0 입니다 — 404 가 아닙니다.

            **실패**: `400 VALIDATION_ERROR` — `yearMonth` 형식 오류""")
    @GetMapping("/monthly")
    public ApiResponse<MonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String yearMonth) {
        return ApiResponse.ok(analysisService.getMonthlySummary(
                authUser.id(), YearMonths.parseOrCurrent(yearMonth)));
    }

    /**
     * {@code weeks} 에는 범위 제약이 있어 {@code yearMonth} 와 달리 {@code @Valid
     * @ModelAttribute} 레코드로 받는다 — 제약을 파라미터에 직접 걸면 500 이 된다
     * ({@link WeeklyTrendRequest} 설명).
     */
    @Operation(summary = "ANL-02 주간 지출 추이", description = """
            막대 그래프용. 월세는 **항상 제외**합니다.

            이번 주 막대만 규칙이 다릅니다: `weekStart`·`weekEnd` 라벨은 월~일 전체인데
            `totalExpense` 는 **월요일~오늘**까지만 더합니다.

            거래가 없는 주도 `0` 으로 내려갑니다 — x축의 막대가 비면 안 되기 때문입니다.

            **실패**: `400 VALIDATION_ERROR` — `weeks` 가 1~52 범위를 벗어남 (초과분을 잘라주지 않고 거부합니다)""")
    @GetMapping("/weekly")
    public ApiResponse<WeeklyTrendResponse> getWeeklyTrend(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute WeeklyTrendRequest request) {
        return ApiResponse.ok(analysisService.getWeeklyTrend(authUser.id(), request));
    }

    /**
     * {@code period}·{@code baseDate} 에 제약 애노테이션은 없지만 레코드로 받는다 —
     * 잘못된 열거형 값이나 날짜 형식이 {@code BindException} 이 되어 어느 필드가 틀렸는지
     * {@code details} 에 담기기 때문이다 ({@link CategoryComparisonRequest} 설명).
     */
    @Operation(summary = "ANL-03 카테고리별 평소 대비 비교", description = """
            `period` 에 따라 기준이 전부 달라집니다.

            | | `WEEK` | `MONTH` |
            |---|---|---|
            | 분석 기간 | 그 주 월요일 ~ `baseDate` | 1일 ~ `baseDate` |
            | 비교 표본 | 직전 4주의 "월요일~같은 요일" | 직전 3개월의 "1일~같은 날짜" |
            | 월세 | 항목·합계에서 제외 | 항목·합계에 포함, 평균·증가율만 제외(`null`) |
            | `isAnomaly` | 넛지 생성 조건과 동일 | 항상 `false` |

            `items` 는 **모든 지출 카테고리**를 ID 순으로 줍니다 — 그 기간에 0원인 것도 포함합니다.

            `dataSufficient: false` 는 비교할 과거 기록이 없다는 뜻이고(가입이 표본 기간보다 늦음),
            그때 `averageAmount`·`growthRate` 는 `null` 입니다.

            **실패**: `400 VALIDATION_ERROR` — `period` 누락·오타, `baseDate` 형식 오류""")
    @GetMapping("/categories")
    public ApiResponse<CategoryComparisonResponse> getCategoryComparison(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @ModelAttribute CategoryComparisonRequest request) {
        return ApiResponse.ok(analysisService.getCategoryComparison(authUser.id(), request));
    }
}
