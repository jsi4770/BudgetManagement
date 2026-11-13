package com.back.was.domain.transaction.dto;

import com.back.was.domain.category.dto.CategoryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * TX-07 응답. 저장하지 않고 추출 결과만 돌려준다 — 프론트가 사용자에게 보여 주고,
 * 확인·수정 후 TX-01 로 저장한다. RCPT-01 도 같은 형식을 쓴다(명세 7장).
 *
 * <p>추출하지 못한 필드는 {@code null} 이고 키는 남는다. 프론트는 {@code missingFields}
 * 를 보고 추가 입력을 요구하므로(SRS 7.1-3) 두 표현이 항상 일치해야 한다 —
 * {@code TransactionService} 가 {@code null} 로부터 목록을 만들어 그 일치를 보장한다.
 */
public record ParsedTransactionResponse(
        Integer amount,
        LocalDate transactionDate,
        CategoryResponse category,
        String memo,
        List<String> missingFields
) {
}
