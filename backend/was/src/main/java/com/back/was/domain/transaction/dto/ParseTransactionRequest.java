package com.back.was.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** TX-07 요청. 규칙은 명세 TX-07 표 그대로 — {@code text} 는 필수 1~200자다. */
public record ParseTransactionRequest(

        @NotBlank
        @Size(min = 1, max = 200)
        String text
) {
}
