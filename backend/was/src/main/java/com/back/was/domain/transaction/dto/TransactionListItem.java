package com.back.was.domain.transaction.dto;

import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.transaction.Transaction;

import java.time.LocalDate;

/**
 * TX-02 목록의 원소.
 *
 * <p>{@link TransactionResponse}(TX-01·TX-04)와 달리 <b>{@code createdAt} 이 없다</b> —
 * 명세 TX-02 예시의 키는 {@code id}·{@code category}·{@code amount}·
 * {@code transactionDate}·{@code memo} 뿐이다. 정렬 기준으로는 쓰이지만 응답에는 나가지
 * 않으므로 타입을 공유하지 않는다.
 */
public record TransactionListItem(
        Long id,
        CategoryResponse category,
        int amount,
        LocalDate transactionDate,
        String memo
) {
    public static TransactionListItem from(Transaction transaction) {
        return new TransactionListItem(
                transaction.getId(),
                CategoryResponse.from(transaction.getCategory()),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getMemo());
    }
}
