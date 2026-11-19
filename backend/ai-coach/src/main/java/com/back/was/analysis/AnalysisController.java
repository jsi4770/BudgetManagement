package com.back.was.analysis;

import com.back.was.common.ApiResponses;
import com.back.was.common.CurrentUserProvider;
import com.back.was.common.InvalidInputException;
import com.back.was.common.TimeUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * ANL-01 GET /analysis/monthly?yearMonth=2026-11
 * ANL-02 GET /analysis/weekly?weeks=4
 * ANL-03 GET /analysis/categories?period=WEEK|MONTH&baseDate=2026-11-04
 * ※ 이미 만들어 둔 분석 API 가 있으면 이 컨트롤러는 빼고 Service 만 가져다 쓰세요.
 */
@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final CurrentUserProvider currentUser;
    private final MonthlySummaryService monthly;
    private final WeeklyTrendService weekly;
    private final CategoryComparisonService comparison;

    public AnalysisController(CurrentUserProvider currentUser, MonthlySummaryService monthly,
                              WeeklyTrendService weekly, CategoryComparisonService comparison) {
        this.currentUser = currentUser;
        this.monthly = monthly;
        this.weekly = weekly;
        this.comparison = comparison;
    }

    @GetMapping("/monthly")
    public Map<String, Object> monthly(@RequestParam(required = false) String yearMonth) {
        YearMonth ym;
        try {
            ym = yearMonth == null ? YearMonth.from(TimeUtil.today()) : YearMonth.parse(yearMonth);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("yearMonth 는 YYYY-MM 형식이에요.", "yearMonth", "FORMAT");
        }
        return ApiResponses.ok(monthly.summary(currentUser.currentUserId(), ym));
    }

    @GetMapping("/weekly")
    public Map<String, Object> weekly(@RequestParam(defaultValue = "4") int weeks) {
        if (weeks < 1 || weeks > 12) {
            throw new InvalidInputException("weeks 는 1~12 사이여야 해요.", "weeks", "RANGE");
        }
        return ApiResponses.ok(Map.of("weeks",
                weekly.weekly(currentUser.currentUserId(), weeks, TimeUtil.today())));
    }

    @GetMapping("/categories")
    public Map<String, Object> categories(@RequestParam(defaultValue = "WEEK") String period,
                                          @RequestParam(required = false) String baseDate) {
        LocalDate base;
        try {
            base = baseDate == null ? TimeUtil.today() : LocalDate.parse(baseDate);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("baseDate 는 YYYY-MM-DD 형식이에요.", "baseDate", "FORMAT");
        }
        Long userId = currentUser.currentUserId();
        return switch (period) {
            case "WEEK" -> ApiResponses.ok(comparison.compareWeek(userId, base));
            case "MONTH" -> ApiResponses.ok(comparison.compareMonth(userId, base));
            default -> throw new InvalidInputException("period 는 WEEK 또는 MONTH 예요.", "period", "ENUM");
        };
    }
}
