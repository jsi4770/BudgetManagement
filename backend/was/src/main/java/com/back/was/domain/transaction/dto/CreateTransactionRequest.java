package com.back.was.domain.transaction.dto;

import com.back.was.global.validation.NotFutureDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * TX-01 요청. 규칙은 명세 TX-01 표를 그대로 옮겼다.
 *
 * <p>한 번에 여러 건을 받는다 — 한 건만 저장할 때도 배열에 하나를 담는다. 1~50건이고,
 * 빈 배열과 51건 이상은 {@code @Size} 가 400 으로 막는다.
 *
 * <p>원소의 검증 실패는 {@code details[].field} 에 {@code transactions[1].amount} 처럼
 * 인덱스(0부터)가 붙어 나간다 — 명세가 요구하는 형식이고, Spring 의 바인딩 경로가 그대로
 * 그 모양이라 따로 변환하지 않는다.
 *
 * <p>직접 입력·한 줄 입력(TX-07)·영수증(RCPT-01)이 모두 이 DTO 로 모인다 — 입력 수단은
 * 저장 형태에 남지 않는다(ERD 5장).
 */
public record CreateTransactionRequest(

        @NotNull
        @Size(min = 1, max = 50)
        List<@NotNull @Valid Item> transactions
) {

    /** 한 건만 담은 요청. 서버 안에서 TX-01 을 부르는 곳(DEV-02)용이다. */
    public static CreateTransactionRequest of(Item item) {
        return new CreateTransactionRequest(List.of(item));
    }

    /**
     * 거래 한 건. 수입/지출을 받는 필드가 없는 게 의도다. 방향은 {@code categoryId} 가
     * 가리키는 카테고리의 {@code TYPE} 으로 결정된다.
     */
    public record Item(

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
}
