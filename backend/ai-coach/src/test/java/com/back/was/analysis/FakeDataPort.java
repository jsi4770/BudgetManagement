package com.back.was.analysis;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 테스트용 메모리 데이터 */
public class FakeDataPort implements AnalysisDataPort {

    public final List<TxRecord> txs = new ArrayList<>();
    public LocalDate signup;
    public Long budget;

    public FakeDataPort add(long categoryId, long amount, LocalDate date, String memo) {
        txs.add(new TxRecord(categoryId, amount, date, memo));
        return this;
    }

    @Override
    public List<TxRecord> findTransactions(Long userId, LocalDate from, LocalDate to) {
        return txs.stream().filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to)).toList();
    }

    @Override
    public LocalDate signupDate(Long userId) {
        return signup;
    }

    @Override
    public Optional<Long> monthlyBudget(Long userId, YearMonth yearMonth) {
        return Optional.ofNullable(budget);
    }

    /** DevSeedService 와 같은 패턴: 17주 평소 소비 + 오늘 배달 3건 급증 */
    public static FakeDataPort seed(LocalDate today, boolean spike) {
        FakeDataPort p = new FakeDataPort();
        LocalDate start = com.back.was.common.TimeUtil.mondayOf(today).minusWeeks(17);
        p.signup = start.minusDays(1);
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue();
            if (d.getDayOfMonth() == 25) p.add(6, 2_500_000, d, "월급");
            if (d.getDayOfMonth() == 1) p.add(5, 550_000, d, "월세");
            if (dow <= 5) p.add(1, 9_000 + (d.getDayOfMonth() % 3) * 1_000L, d, "점심");
            if (dow != 7) p.add(3, 4_500, d, "메가커피");
            if (dow == 2 || dow == 4) p.add(4, 12_000, d, "택시");
            if (dow == 3) p.add(2, 21_000, d, "배민");
            if (dow == 6) p.add(2, 23_000, d, "요기요");
        }
        if (spike) {
            p.add(2, 34_000, today, "배민 치킨").add(2, 28_000, today, "요기요").add(2, 31_000, today, "쿠팡이츠");
        }
        return p;
    }
}
