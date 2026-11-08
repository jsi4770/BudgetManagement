package com.back.was.domain.analysis;

import com.back.was.domain.analysis.dto.CategoryComparisonRequest;
import com.back.was.domain.analysis.dto.CategoryComparisonResponse;
import com.back.was.domain.analysis.dto.MonthlySummaryResponse;
import com.back.was.domain.analysis.dto.WeeklyTrendRequest;
import com.back.was.domain.analysis.dto.WeeklyTrendResponse;
import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.BudgetService;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.dto.CategoryRefResponse;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.transaction.AmountSummary;
import com.back.was.domain.transaction.CategoryAmountSum;
import com.back.was.domain.transaction.DailyAmount;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Months;
import com.back.was.global.support.Weeks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 명세 8장 소비 분석(ANALYSIS). <b>분석 결과를 저장하지 않는다</b> — 명세 8장 머리말대로
 * 조회 시점에 TRANSACTION 을 집계한다. 그래서 거래 후처리(넛지·챌린지)가 아직 없어도 이
 * 숫자는 항상 맞는다.
 *
 * <p>모든 집계 쿼리에 {@code userId} 가 들어가므로 남의 거래는 결과에 섞이지 않는다
 * (서버_내부_처리 공통 전제). 경로·쿼리에 사용자 ID 가 없어 범위를 바꿔 찔러볼 수단도 없다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BudgetService budgetService;

    /**
     * ANL-01 월별 요약.
     *
     * <p><b>{@code readOnly} 가 아닌 이유</b>: 예산 자동 복사(서버_내부_처리 5장)가 INSERT
     * 를 할 수 있다. 명세 ANL-01 의 {@code remainingBudget} 설명이 "이번 달 예산 없으면 최근
     * 예산 복사 후 계산"이라 BUDGET-01 과 같은 규칙을 타야 하고, 그래서 규칙을 복제하지 않고
     * {@link BudgetService#findOrCopy} 를 호출한다.
     *
     * <p>예산을 찾지 못해도 404 가 아니다 — 명세 ANL-01 은 그 상황을
     * {@code remainingBudget: null} 로 표현한다. 404 는 BUDGET-01 쪽의 "초기 설정 화면으로
     * 보내라" 신호다.
     *
     * <p>거래가 한 건도 없는 달도 200 + 0 원이다. 홈 화면은 가입 직후에도 열려야 한다.
     */
    @Transactional
    public MonthlySummaryResponse getMonthlySummary(Long userId, YearMonth yearMonth) {
        DateRange month = Months.monthOf(yearMonth);

        AmountSummary summary = transactionRepository.sumAmounts(
                userId, month.start(), month.end(), CategoryType.INCOME, CategoryType.EXPENSE);

        Integer monthlyBudget = budgetService.findOrCopy(userId, yearMonth)
                .map(Budget::getMonthlyBudget)
                .orElse(null);

        return MonthlySummaryResponse.of(yearMonth, summary, monthlyBudget);
    }

    /**
     * ANL-02 주간 지출 추이. <b>월세는 항상 제외</b>한다 (ERD 4장 · 화면 "이번 주
     * 지출(월세 제외)").
     *
     * <p>조회 범위가 가장 오래된 주의 월요일부터 <b>오늘</b>까지인 것이 이 메서드의 핵심이다.
     * 그 덕분에 주별로 담을 때 라벨 기간({@code 월~일}) 그대로 걸러도 이번 주 합계는 자동으로
     * "월요일 ~ 오늘"이 된다 (명세 ANL-02 본문). 범위를 이번 주 일요일까지로 늘리면 그 규칙이
     * 깨진다 — TX-01 이 미래 날짜를 막으므로 실제로 값이 달라질 일은 없지만, 규칙을 다른
     * 검증에 의존시키지 않는다.
     *
     * <p>집계는 일별로 받아 애플리케이션에서 주별로 담는다. 주 단위 {@code group by} 를 쓰면
     * 월요일 시작·KST 라는 주 경계 정의가 DB 함수로 흘러가 Postgres(운영)와 H2(테스트)에서
     * 같은 결과를 보장하기 어렵다 — 경계의 정의는 {@link Weeks} 한 곳에만 둔다.
     *
     * <p>{@code readOnly} 인 이유: ANL-01 과 달리 예산을 보지 않으므로 쓰기가 없다.
     */
    @Transactional(readOnly = true)
    public WeeklyTrendResponse getWeeklyTrend(Long userId, WeeklyTrendRequest request) {
        int weeks = request.weeksOrDefault();
        LocalDate today = KstTime.today();

        List<DailyAmount> dailyExpenses = transactionRepository.sumDailyExpenses(
                userId,
                Weeks.mondayOf(today).minusWeeks(weeks - 1L),
                today,
                CategoryType.EXPENSE,
                RentCategory.ID);

        // 오래된 주 → 이번 주 순 (명세 ANL-02 예시 순서). 거래가 없는 주도 0원으로 들어간다.
        List<WeeklyTrendResponse.Week> bars = new ArrayList<>(weeks);
        for (int weeksAgo = weeks - 1; weeksAgo >= 0; weeksAgo--) {
            DateRange week = Weeks.weekOf(today.minusWeeks(weeksAgo));
            bars.add(WeeklyTrendResponse.Week.of(week, sumWithin(dailyExpenses, week)));
        }

        return new WeeklyTrendResponse(List.copyOf(bars));
    }

    private static long sumWithin(List<DailyAmount> dailyExpenses, DateRange week) {
        return dailyExpenses.stream()
                .filter(daily -> week.contains(daily.date()))
                .mapToLong(DailyAmount::amount)
                .sum();
    }

    /**
     * ANL-03 카테고리별 평소 대비 비교. {@code WEEK}/{@code MONTH} 의 차이는 전부
     * {@link AnalysisPeriod} 에 있고, 여기서는 그 구간들을 집계해 비교에 넘긴다.
     *
     * <p>평균·증가율은 {@link SpendingComparison} 이 낸다 — 넛지 판정과 <b>같은 계산
     * 함수</b>여야 한다는 서버_내부_처리 3장의 요구가 이 호출이다. 따로 계산하면 카드의
     * "평소 평균"과 이 화면의 평균이 어긋난다.
     *
     * <p><b>표본 구간은 구간마다 한 번씩 조회한다</b> ({@code WEEK} 4번, {@code MONTH} 3번).
     * 한 번에 긁어 와 쪼갤 수도 있지만 {@code MONTH} 의 표본은 "직전 3개월의 1~N일"로
     * 서로 떨어져 있어서, 코드에서 날짜를 구간에 되배치하는 일이 생긴다. 구간당 한 번이면
     * 집계 범위와 구간 정의가 1:1 로 맞고, 한 사용자의 작은 집계 쿼리 3~4개다.
     *
     * <p>데이터가 부족하면 표본을 <b>아예 조회하지 않는다</b> — 평균을 내지 않을 것이므로
     * (명세 ANL-03 {@code dataSufficient}) 쿼리도 낭비다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 토큰은 유효하나 사용자 행이 없을 때
     */
    @Transactional(readOnly = true)
    public CategoryComparisonResponse getCategoryComparison(Long userId, CategoryComparisonRequest request) {
        AnalysisPeriod period = request.periodOrDefault();
        LocalDate baseDate = request.baseDateOrDefault();
        DateRange analysisRange = period.analysisRange(baseDate);
        Long excludedCategoryId = period.isRentExcluded() ? RentCategory.ID : null;

        boolean dataSufficient = isDataSufficient(userId, period, baseDate);

        Map<Long, Long> amounts = sumByCategory(userId, analysisRange, excludedCategoryId);
        List<Map<Long, Long>> samples = dataSufficient
                ? sampleSumsByCategory(userId, period, baseDate, excludedCategoryId)
                : List.of();

        List<CategoryComparisonResponse.Item> items =
                categoryRepository.findAllByTypeOrderByIdAsc(CategoryType.EXPENSE).stream()
                        .filter(category -> !period.isRentExcluded() || !RentCategory.isRent(category.getId()))
                        .map(category -> toItem(period, category, dataSufficient, amounts, samples))
                        .toList();

        return CategoryComparisonResponse.of(period, analysisRange, dataSufficient, items);
    }

    /**
     * 명세 ANL-03 {@code dataSufficient} — 가입일이 가장 오래된 표본 구간의 시작보다 뒤면
     * 비교할 기록이 없다. 경계는 포함이다(가입일 = 그 날짜면 충분).
     */
    private boolean isDataSufficient(Long userId, AnalysisPeriod period, LocalDate baseDate) {
        LocalDate signUpDate = userRepository.findById(userId)
                .orElseThrow(BusinessException::notFound)
                .getCreatedAt()
                .toLocalDate();

        return !signUpDate.isAfter(period.oldestSampleStart(baseDate));
    }

    /** 오래된 구간 → 직전 구간 순. 순서가 평균·표준편차를 바꾸지는 않지만 디버깅에 읽기 쉽다. */
    private List<Map<Long, Long>> sampleSumsByCategory(Long userId, AnalysisPeriod period,
                                                       LocalDate baseDate, Long excludedCategoryId) {
        List<Map<Long, Long>> samples = new ArrayList<>(period.sampleCount());
        for (int periodsAgo = period.sampleCount(); periodsAgo >= 1; periodsAgo--) {
            samples.add(sumByCategory(userId, period.sampleRange(baseDate, periodsAgo), excludedCategoryId));
        }
        return samples;
    }

    private Map<Long, Long> sumByCategory(Long userId, DateRange range, Long excludedCategoryId) {
        return transactionRepository
                .sumExpensesByCategory(userId, range.start(), range.end(),
                        CategoryType.EXPENSE, excludedCategoryId)
                .stream()
                .collect(Collectors.toMap(CategoryAmountSum::categoryId, CategoryAmountSum::amount));
    }

    /**
     * 카테고리 한 줄. 지출이 없는 카테고리도 0원으로 들어간다(명세 ANL-03 의 비교 화면은
     * 평소 쓰던 항목이 0원이 된 것도 보여 준다 — docs/시연_데이터.md 6-1·6-3 표).
     *
     * <p>비교하지 <b>않는</b> 두 경우가 여기서 갈린다: 데이터 부족과 월세({@code MONTH} 의
     * 월세는 항목·합계에는 있고 평균·증가율만 {@code null} 이다 — 납부일이 달마다 다르면
     * 같은 기간 비교가 왜곡된다, ERD 4장).
     */
    private static CategoryComparisonResponse.Item toItem(AnalysisPeriod period, Category category,
                                                          boolean dataSufficient,
                                                          Map<Long, Long> amounts,
                                                          List<Map<Long, Long>> samples) {
        Long categoryId = category.getId();
        CategoryRefResponse ref = CategoryRefResponse.from(category);
        int amount = toAmount(amounts.getOrDefault(categoryId, 0L));

        if (!dataSufficient || RentCategory.isRent(categoryId)) {
            return new CategoryComparisonResponse.Item(ref, amount, null, null, false);
        }

        // 기록이 없는 구간은 0원으로 채운다 (ERD 6장). 빼먹으면 표본이 줄어 평균이 커진다.
        List<Integer> sampleAmounts = samples.stream()
                .map(sample -> toAmount(sample.getOrDefault(categoryId, 0L)))
                .toList();
        SpendingComparison comparison = SpendingComparison.of(amount, sampleAmounts);

        return new CategoryComparisonResponse.Item(
                ref,
                amount,
                comparison.average(),
                comparison.growthRate(),
                period.isAnomalyDetected() && comparison.isAnomaly());
    }

    /** JPQL {@code sum()} 의 {@code Long} 을 명세의 int(원)로 좁힌다. */
    private static int toAmount(long sum) {
        return Math.toIntExact(sum);
    }
}
