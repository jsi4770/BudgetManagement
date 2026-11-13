package com.back.was.analysis;

import com.back.was.category.CategoryInfo;
import com.back.was.category.CategoryProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * ANL-01 월별 요약 (홈 상단).
 * balance = 총수입 − 총지출 / remainingBudget = 예산 − 총지출 / budgetUsageRate = 총지출 ÷ 예산 × 100
 * 예산이 없으면 remainingBudget·budgetUsageRate·budget 은 null
 */
@Service
public class MonthlySummaryService {

    public record MonthlySummary(String yearMonth, long totalIncome, long totalExpense, long balance,
                                 Long budget, Long remainingBudget, Double budgetUsageRate) {}

    private final AnalysisDataPort port;
    private final CategoryProvider categories;

    public MonthlySummaryService(AnalysisDataPort port, CategoryProvider categories) {
        this.port = port;
        this.categories = categories;
    }

    public MonthlySummary summary(Long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<TxRecord> txs = port.findTransactions(userId, from, to);

        long income = 0;
        long expense = 0;
        for (TxRecord t : txs) {
            Optional<CategoryInfo> c = categories.findById(t.categoryId());
            if (c.isEmpty()) continue;
            if (c.get().isExpense()) expense += t.amount();
            else income += t.amount();
        }

        Long budget = port.monthlyBudget(userId, ym).orElse(null);
        Long remaining = budget == null ? null : budget - expense;
        Double usage = (budget == null || budget == 0) ? null : AnomalyJudge.round2(expense * 100.0 / budget);

        return new MonthlySummary(ym.toString(), income, expense, income - expense, budget, remaining, usage);
    }
}
