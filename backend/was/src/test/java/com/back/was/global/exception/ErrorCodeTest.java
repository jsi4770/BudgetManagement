package com.back.was.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 명세서 0장 4) 표와 enum을 1:1로 대조한다. 기대값은 명세 표를 그대로 옮긴
 * 것이므로, 이 테스트가 깨지면 enum이 아니라 명세와의 계약이 깨진 것이다.
 */
class ErrorCodeTest {

    /** 명세 0장 4) 표의 (code, HTTP) — 표에 적힌 순서 그대로. */
    private static final List<String> SPEC_TABLE = List.of(
            "VALIDATION_ERROR:400",
            "UNAUTHORIZED:401",
            "TOKEN_EXPIRED:401",
            "INVALID_CREDENTIALS:401",
            "INVALID_REFRESH_TOKEN:401",
            "FORBIDDEN:403",
            "NOT_FOUND:404",
            "EMAIL_DUPLICATED:409",
            "CHALLENGE_ALREADY_IN_PROGRESS:409",
            "COACHING_ALREADY_ENDED:409",
            "FILE_TOO_LARGE:413",
            "UNSUPPORTED_FILE_TYPE:415",
            "AI_PARSE_FAILED:422",
            "OCR_FAILED:502",
            "AI_UNAVAILABLE:503",
            "INTERNAL_ERROR:500"
    );

    @Test
    @DisplayName("명세 0장 4)의 16개 코드와 HTTP 상태가 순서까지 정확히 일치한다")
    void matchesSpecTable() {
        List<String> actual = Arrays.stream(ErrorCode.values())
                .map(c -> c.name() + ":" + c.getStatus().value())
                .toList();

        assertThat(actual).containsExactlyElementsOf(SPEC_TABLE);
    }

    @Test
    @DisplayName("명세에 없는 코드가 추가되지 않았다")
    void hasNoExtraCodes() {
        assertThat(ErrorCode.values()).hasSize(16);
    }

    @Test
    @DisplayName("모든 코드가 사용자에게 보여줄 기본 메시지를 갖는다")
    void everyCodeHasMessage() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getMessage))
                .allSatisfy(message -> assertThat(message).isNotBlank());
    }
}
