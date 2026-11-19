package com.back.was.analysis;

import com.back.was.dev.InMemoryLedger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** 단독 실행용 메모리 구현. 팀 DB 연결 시 JPA 쿼리로 교체 */
@Component
public class AnalysisDataPortAdapter implements AnalysisDataPort {

    private final InMemoryLedger ledger;

    public AnalysisDataPortAdapter(InMemoryLedger ledger) { this.ledger = ledger; }

    @Override
    public List<TxRecord> findTransactions(Long userId, LocalDate from, LocalDate to) {
        return ledger.all(userId).stream().filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to)).toList();
    }

    @Override
    public LocalDate signupDate(Long userId) { return ledger.signupDate(userId); }

    @Override
    public Optional<Long> monthlyBudget(Long userId, YearMonth yearMonth) { return Optional.empty(); }
}
