package com.back.was.analysis;

import com.back.was.category.CategoryProvider;
import com.back.was.common.TimeUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ANL-02 주간 지출 추이. 월세 항상 제외. 이번 주 = 월요일 ~ 오늘 합계.
 * weekEnd 는 응답 예시대로 그 주 일요일로 표시.
 */
@Service
public class WeeklyTrendService {

    public record WeekTotal(String weekStart, String weekEnd, long totalExpense) {}

    private final AnalysisDataPort port;
    private final CategoryProvider categories;

    public WeeklyTrendService(AnalysisDataPort port, CategoryProvider categories) {
        this.port = port;
        this.categories = categories;
    }

    public List<WeekTotal> weekly(Long userId, int weeks, LocalDate today) {
        LocalDate thisMonday = TimeUtil.mondayOf(today);
        LocalDate from = thisMonday.minusWeeks(weeks - 1L);

        List<TxRecord> txs = port.findTransactions(userId, from, today).stream()
                .filter(t -> categories.isExpense(t.categoryId()) && !categories.isRent(t.categoryId()))
                .toList();

        List<WeekTotal> result = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate s = thisMonday.minusWeeks(i);
            LocalDate e = s.plusDays(6);
            LocalDate sumEnd = e.isAfter(today) ? today : e;     // 이번 주는 오늘까지만
            long total = txs.stream()
                    .filter(t -> !t.date().isBefore(s) && !t.date().isAfter(sumEnd))
                    .mapToLong(TxRecord::amount).sum();
            result.add(new WeekTotal(s.toString(), e.toString(), total));
        }
        return result;
    }
}
