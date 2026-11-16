package com.back.was.domain.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TX-01 요청 본문 — 거래 여러 건 (명세 TX-01). 한 건만 저장할 때도 이 배열에 담아 보낸다.
 *
 * <p>원소마다 {@code @Valid} 라 검증 실패의 {@code details[].field} 가 {@code transactions[1].amount}
 * 처럼 몇 번째 거래인지를 담는다 — {@code BindException} 의 필드 경로가 그대로 쓰인다.
 *
 * <p>원소의 {@code @NotNull} 이 없으면 {@code [null]} 이 검증을 지나 서비스에서 NPE(500)가 된다.
 *
 * @param transactions 1건 이상 {@value #MAX_SIZE}건 이하. 상한은 명세가 정한 값이다 — 요청 하나가
 *                     한 트랜잭션이라, 상한이 없으면 요청 하나로 커넥션과 락을 무한정 쥘 수 있다
 */
public record CreateTransactionsRequest(

        @NotNull
        @Size(min = 1, max = MAX_SIZE)
        List<@NotNull @Valid CreateTransactionRequest> transactions
) {
    public static final int MAX_SIZE = 50;
}
