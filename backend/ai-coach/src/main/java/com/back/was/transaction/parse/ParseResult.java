package com.back.was.transaction.parse;

import com.back.was.category.CategoryDto;

import java.util.List;

/**
 * TX-07 / RCPT-01 공통 응답 data.
 * 저장하지 않고 이 결과만 돌려주면, 프론트가 확인·수정 후 TX-01 로 저장한다.
 *
 * @param missingFields 추출 실패한 필수 필드 (예: ["amount"]) → 프론트가 추가 입력 요청 (SRS 7.1-3)
 */
public record ParseResult(
        Long amount,
        String transactionDate,
        CategoryDto category,
        String memo,
        List<String> missingFields
) {}
