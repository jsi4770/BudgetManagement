package com.back.was.domain.transaction.dto;

import com.back.was.global.validation.NotFutureDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * TX-01 요청. 규칙은 명세 TX-01 표를 그대로 옮겼다.
 *
 * <p>직접 입력·한 줄 입력(TX-07)·영수증(RCPT-01)이 모두 이 DTO 로 모인다 — 입력 수단은
 * 저장 형태에 남지 않는다(ERD 5장).
 *
 * <p>수입/지출을 받는 필드가 없는 게 의도다. 방향은 {@code categoryId} 가 가리키는
 * 카테고리의 {@code TYPE} 으로 결정된다.
 */
public record CreateTransactionRequest(

        @NotNull
        Long categoryId,

        /** 1 이상. 부호는 카테고리 TYPE 이 정하므로 음수·0 은 받지 않는다. */
        @NotNull
        @Min(1)
        Integer amount,

        @NotNull
        @NotFutureDate
        LocalDate transactionDate,

        @Size(max = 255)
        String memo
) {
}
