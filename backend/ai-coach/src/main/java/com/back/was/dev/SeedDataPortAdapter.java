package com.back.was.dev;

import com.back.was.analysis.TxRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 단독 실행용 메모리 구현. 시연 계정은 DEMO_USER_ID 하나 */
@Component
public class SeedDataPortAdapter implements SeedDataPort {

    private final InMemoryLedger ledger;

    public SeedDataPortAdapter(InMemoryLedger ledger) { this.ledger = ledger; }

    @Override
    public boolean isDemoAccount(Long userId) { return InMemoryLedger.DEMO_USER_ID == userId; }

    @Override
    public void deleteAllTransactions(Long userId) { ledger.clear(userId); }

    @Override
    public void setSignupDate(Long userId, LocalDate signupDate) { ledger.setSignupDate(userId, signupDate); }

    @Override
    public void saveTransaction(Long userId, Long categoryId, long amount, LocalDate date, String memo) {
        ledger.add(userId, new TxRecord(categoryId, amount, date, memo));
    }
}
