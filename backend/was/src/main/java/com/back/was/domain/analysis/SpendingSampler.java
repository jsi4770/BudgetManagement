package com.back.was.domain.analysis;

import com.back.was.domain.category.CategoryType;
import com.back.was.domain.transaction.CategoryAmountSum;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.global.support.DateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 카테고리별 지출 집계를 읽는다 — ANL-03 과 넛지 판정이 공유하는 <b>단 하나의</b> 조회
 * 경로다(서버_내부_처리 3장 "같은 계산 함수").
 *
 * <p>계산은 하지 않는다. 숫자를 모아 {@link SpendingSamples} 로 넘기고, 평균·Z 는
 * {@link SpendingComparison} 이 낸다.
 */
@Component
@RequiredArgsConstructor
public class SpendingSampler {

    private final TransactionRepository transactionRepository;

    /** 한 구간의 카테고리별 지출 합계. 거래가 없는 카테고리는 키가 없다. */
    public Map<Long, Long> amountsIn(Long userId, DateRange range, Long excludedCategoryId) {
        return transactionRepository
                .sumExpensesByCategory(userId, range.start(), range.end(),
                        CategoryType.EXPENSE, excludedCategoryId)
                .stream()
                .collect(Collectors.toMap(CategoryAmountSum::categoryId, CategoryAmountSum::amount));
    }

    /**
     * 표본 구간들의 집계. <b>오래된 구간부터</b> — 순서가 평균·표준편차를 바꾸지는 않지만
     * 디버깅할 때 읽기 쉽다.
     *
     * <p>구간마다 쿼리를 한 번씩 돈다({@code WEEK} 4번, {@code MONTH} 3번). 한 번에 긁어
     * 와 쪼갤 수도 있지만 {@code MONTH} 의 표본은 "직전 3개월의 1~N일"로 서로 떨어져
     * 있어서, 코드에서 날짜를 구간에 되배치하는 일이 생긴다. 구간당 한 번이면 집계 범위와
     * 구간 정의가 1:1 로 맞는다.
     */
    public List<Map<Long, Long>> samplesOf(Long userId, AnalysisPeriod period,
                                           LocalDate baseDate, Long excludedCategoryId) {
        List<Map<Long, Long>> samples = new ArrayList<>(period.sampleCount());
        for (int periodsAgo = period.sampleCount(); periodsAgo >= 1; periodsAgo--) {
            samples.add(amountsIn(userId, period.sampleRange(baseDate, periodsAgo), excludedCategoryId));
        }
        return samples;
    }

    /**
     * 분석 기간과 표본 구간을 한 번에 — 넛지 판정처럼 둘 다 필요한 경우용.
     *
     * <p>ANL-03 은 이 메서드를 쓰지 않는다. 데이터가 부족하면 표본을 <b>아예 조회하지
     * 않아야</b> 해서({@code dataSufficient: false} 면 평균을 내지 않는다) 두 호출을 따로
     * 한다.
     */
    public SpendingSamples collect(Long userId, AnalysisPeriod period, LocalDate baseDate,
                                   Long excludedCategoryId) {
        return new SpendingSamples(
                amountsIn(userId, period.analysisRange(baseDate), excludedCategoryId),
                samplesOf(userId, period, baseDate, excludedCategoryId));
    }
}
