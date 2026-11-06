package com.back.was.domain.transaction.dto;

import com.back.was.global.validation.NotFutureDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * TX-05 요청. 명세대로 <b>보낸 필드만</b> 수정하므로 모든 필드가 선택이고,
 * {@code null} 은 "바꾸지 않음"을 뜻한다.
 *
 * <p>명세 TX-05 에는 요청 필드 표가 없고 예시에 {@code amount} · {@code memo} 만 있다.
 * 그래도 네 필드를 모두 받는 근거는 같은 절의 서버 후처리 설명이다 — "카테고리 변경 시
 * 이전·새 카테고리 모두 판정", "{@code transactionDate} 변경 시에도 미래 날짜 불가"는
 * 둘 다 수정 가능함을 전제한다. 규칙은 TX-01 표를 그대로 따른다.
 *
 * <p><b>메모 삭제는 빈 문자열로 보낸다.</b> JSON 에서 "키를 안 보냄"과 "키를 null 로
 * 보냄"은 이 레코드로 구분되지 않는데, 명세에 메모를 지우는 방법이 정의돼 있지 않다.
 * {@code ""} 와 공백을 "없음"으로 정규화하는 건 TX-01 과 같은 규칙이므로(엔티티의
 * blank → null), 그 경로를 삭제 수단으로 쓴다. {@code null} 은 다른 필드와 똑같이
 * "유지"다.
 */
public record UpdateTransactionRequest(

        Long categoryId,

        @Min(1)
        Integer amount,

        @NotFutureDate
        LocalDate transactionDate,

        @Size(max = 255)
        String memo
) {
    public boolean hasCategoryId() {
        return categoryId != null;
    }

    public boolean hasAmount() {
        return amount != null;
    }

    public boolean hasTransactionDate() {
        return transactionDate != null;
    }

    public boolean hasMemo() {
        return memo != null;
    }
}
