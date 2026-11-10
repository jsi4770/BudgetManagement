package com.back.was.domain.ocr;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OCR 어댑터가 없는 동안의 실제 동작 — 항상 502.
 *
 * <p>{@code UnavailableTransactionTextParserTest}(TX-07)와 같은 성격의 테스트다. 그럴듯한
 * 가짜 텍스트를 돌려주도록 바꾸면 여기서 깨진다 — 그게 이 테스트의 목적이다. 미구현을
 * 숨기지 않겠다는 결정을 고정한다.
 */
class UnavailableReceiptOcrClientTest {

    private final UnavailableReceiptOcrClient client = new UnavailableReceiptOcrClient();

    @Test
    @DisplayName("항상 502 OCR_FAILED — 가짜 텍스트를 만들지 않는다")
    void alwaysFails() {
        ReceiptImage image = new ReceiptImage("fake-jpeg".getBytes(), "image/jpeg");

        assertThatThrownBy(() -> client.extractText(image))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OCR_FAILED);
    }

    @Test
    @DisplayName("ReceiptImage 의 toString 은 원본을 노출하지 않는다 (SNFR-008: 로그에도 남기지 않음)")
    void toStringHidesTheImage() {
        ReceiptImage image = new ReceiptImage("secret-receipt-content".getBytes(), "image/png");

        assertThat(image.toString())
                .contains("image/png")
                .contains("22")                       // 바이트 길이
                .doesNotContain("secret-receipt");
    }
}
