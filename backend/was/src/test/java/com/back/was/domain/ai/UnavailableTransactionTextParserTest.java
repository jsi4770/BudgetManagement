package com.back.was.domain.ai;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.support.KstTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AI 어댑터가 없는 동안의 기본 구현체. */
class UnavailableTransactionTextParserTest {

    @Test
    @DisplayName("어댑터가 없으면 가짜 결과가 아니라 AI_UNAVAILABLE 을 던진다")
    void alwaysUnavailable() {
        TransactionTextParser parser = new UnavailableTransactionTextParser();
        TextParseCommand command = new TextParseCommand(
                "어제 저녁에 배달 음식 18,000원 썼어", KstTime.today(), List.of());

        assertThatThrownBy(() -> parser.parse(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);
    }
}
