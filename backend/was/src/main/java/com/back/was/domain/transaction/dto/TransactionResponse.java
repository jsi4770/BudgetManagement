package com.back.was.domain.transaction.dto;

import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.transaction.Transaction;
import com.back.was.global.support.KstTime;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * TX-01 응답.
 *
 * <p>카테고리는 ID 가 아니라 {@code {id, name, type}} 객체로 나간다 — 저장 직후 화면이
 * 카테고리명을 다시 조회하지 않아도 되게 하려는 명세의 의도다. 키 구성이 CAT-01 의
 * 원소와 완전히 같으므로 {@link CategoryResponse} 를 공유한다.
 *
 * <p>{@code transactionDate} 는 날짜만(명세 0장 2)), {@code createdAt} 은 오프셋 포함
 * ISO 8601 이라 {@link KstTime#toOffset} 을 거친다.
 */
public record TransactionResponse(
        Long id,
        CategoryResponse category,
        int amount,
        LocalDate transactionDate,
        String memo,
        OffsetDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                CategoryResponse.from(transaction.getCategory()),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getMemo(),
                KstTime.toOffset(transaction.getCreatedAt()));
    }
}
