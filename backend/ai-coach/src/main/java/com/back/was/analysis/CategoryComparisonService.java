package com.back.was.analysis;

import com.back.was.category.CategoryInfo;
import com.back.was.category.CategoryProvider;
import com.back.was.category.CategoryRef;
import com.back.was.common.TimeUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * ANL-03 카테고리별 평소 대비 비교.
 *
 * WEEK : 이번 주 = 월요일 ~ baseDate. 평균 = 직전 4주 각각의 "월요일 ~ 같은 요일" 합계 평균 (빈 주 0원 포함)
 *        월세 제외. 가입 4주 미만이면 dataSufficient=false, 평균·증가율 null, 이상치 false
 * MONTH: 이번 달 1일 ~ baseDate. 평균 = 직전 3개월 "1일 ~ 같은 날짜" 평균 (같은 날짜 없는 달은 그 달 전체)
 *        월세는 금액만 (평균·증가율 null). isAnomaly 는 항상 false
 *        가입일이 직전 3개월 첫날 이후면 dataSufficient=false
 */
@Service
public class CategoryComparisonService {

    private final AnalysisDataPort port;
    private final CategoryProvider categories;

    public CategoryComparisonService(AnalysisDataPort port, CategoryProvider categories) {
        this.port = port;
        this.categories = categories;
    }

    // ------------------------------------------------------------------ WEEK

    public CategoryComparison compareWeek(Long userId, LocalDate base) {
        LocalDate start = TimeUtil.mondayOf(base);
        int span = (int) ChronoUnit.DAYS.between(start, base);   // 0(월) ~ 6(일)
        boolean sufficient = AnomalyJudge.isDataSufficient(port.signupDate(userId), base);

        // 지출만, 월세 제외
        List<TxRecord> txs = port.findTransactions(userId, start.minusWeeks(4), base).stream()
                .filter(t -> categories.isExpense(t.categoryId()) && !categories.isRent(t.categoryId()))
                .toList();

        List<CategoryComparison.Item> items = new ArrayList<>();
        for (Long catId : new TreeSet<>(txs.stream().map(TxRecord::categoryId).toList())) {
            long current = sum(txs, catId, start, base);
            List<Long> past = new ArrayList<>();
            for (int k = 1; k <= 4; k++) {
                LocalDate s = start.minusWeeks(k);
                past.add(sum(txs, catId, s, s.plusDays(span)));
            }

            Long avg = null;
            Double growth = null;
            boolean anomaly = false;
            if (sufficient) {
                AnomalyJudge.Result r = AnomalyJudge.judge(current, past);
                avg = r.averageAmount();
                growth = r.growthRate();
                anomaly = r.anomaly();
            }
            if (current == 0 && (avg == null || avg == 0)) continue;   // 이번 주·평소 모두 0이면 생략

            items.add(new CategoryComparison.Item(ref(catId), current, avg, growth, anomaly));
        }

        long total = items.stream().mapToLong(CategoryComparison.Item::amount).sum();
        return new CategoryComparison("WEEK", start.toString(), base.toString(), sufficient, total, items);
    }

    /** 넛지 판정용: 이번 주 특정 카테고리 결과 (없으면 empty) */
    public Optional<CategoryComparison.Item> weekItem(CategoryComparison week, Long categoryId) {
        return week.items().stream().filter(i -> i.category().id().equals(categoryId)).findFirst();
    }

    // ------------------------------------------------------------------ MONTH

    public CategoryComparison compareMonth(Long userId, LocalDate base) {
        LocalDate start = base.withDayOfMonth(1);
        LocalDate windowStart = start.minusMonths(3);
        boolean sufficient = !port.signupDate(userId).isAfter(windowStart);

        // 지출만 (월세 포함)
        List<TxRecord> txs = port.findTransactions(userId, windowStart, base).stream()
                .filter(t -> categories.isExpense(t.categoryId()))
                .toList();

        List<CategoryComparison.Item> items = new ArrayList<>();
        for (Long catId : new TreeSet<>(txs.stream().map(TxRecord::categoryId).toList())) {
            long current = sum(txs, catId, start, base);

            if (categories.isRent(catId)) {
                // 월세: 납부일이 달마다 달라 같은 기간 비교가 왜곡 → 금액만 (명세)
                if (current > 0) items.add(new CategoryComparison.Item(ref(catId), current, null, null, false));
                continue;
            }

            Long avg = null;
            Double growth = null;
            if (sufficient) {
                long pastSum = 0;
                for (int k = 1; k <= 3; k++) {
                    LocalDate ms = start.minusMonths(k);
                    LocalDate me = ms.withDayOfMonth(Math.min(base.getDayOfMonth(), ms.lengthOfMonth()));
                    pastSum += sum(txs, catId, ms, me);
                }
                double avgD = pastSum / 3.0;
                avg = Math.round(avgD);
                growth = avgD > 0 ? AnomalyJudge.round2((current - avgD) / avgD * 100) : null;
            }
            if (current == 0 && (avg == null || avg == 0)) continue;

            items.add(new CategoryComparison.Item(ref(catId), current, avg, growth, false));   // MONTH 는 항상 false
        }

        long total = items.stream().mapToLong(CategoryComparison.Item::amount).sum();
        return new CategoryComparison("MONTH", start.toString(), base.toString(), sufficient, total, items);
    }

    // ------------------------------------------------------------------ 공통

    private static long sum(List<TxRecord> txs, Long catId, LocalDate from, LocalDate to) {
        return txs.stream()
                .filter(t -> t.categoryId().equals(catId) && !t.date().isBefore(from) && !t.date().isAfter(to))
                .mapToLong(TxRecord::amount)
                .sum();
    }

    private CategoryRef ref(Long catId) {
        CategoryInfo c = categories.findById(catId).orElse(categories.etc());
        return CategoryRef.of(c);
    }
}
