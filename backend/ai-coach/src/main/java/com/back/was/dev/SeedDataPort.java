package com.back.was.dev;

import java.time.LocalDate;

/** DEV-01 시드 데이터가 DB 에 쓰는 창구. SeedDataPortAdapter 에 구현 */
public interface SeedDataPort {

    /** 시연 계정인지 (예: 정해 둔 이메일). 아니면 403 */
    boolean isDemoAccount(Long userId);

    /** 다시 만들 때 기존 거래를 비움 (시연 계정만) */
    void deleteAllTransactions(Long userId);

    /** 가입일을 첫 거래 이전으로 (가입 4주 미만이면 dataSufficient: false 가 되므로) */
    void setSignupDate(Long userId, LocalDate signupDate);

    /** TX-01 과 같은 필드로 저장: categoryId, amount, transactionDate, memo */
    void saveTransaction(Long userId, Long categoryId, long amount, LocalDate transactionDate, String memo);
}
