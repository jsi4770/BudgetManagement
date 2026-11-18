package com.back.was.domain.ocr;

import com.back.was.domain.ai.AiTestFixtures.FakeGemini;
import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OCR 서버가 없을 때(ocr.base-url 비어 있음)의 대체 경로: Gemini 가 이미지를 직접 읽는다. */
class GeminiReceiptOcrClientTest {

    private static final byte[] SMALL_JPEG = TestImages.jpeg(400, 600);
    private static final ReceiptImage IMAGE = new ReceiptImage(SMALL_JPEG, "image/jpeg");

    @Test
    @DisplayName("이미지 바이트·타입이 Gemini 로 넘어가고 결과가 ParsedTransaction 이 된다")
    void readsImageDirectly() {
        FakeGemini gemini = new FakeGemini("""
                {"isTransaction":true,"amount":23500,"transactionDate":"2026-11-04","categoryId":2,"memo":"배달의민족 주문"}
                """);

        ParsedTransaction p = new GeminiReceiptOcrClient(gemini, new ReceiptPromptBuilder())
                .analyze(IMAGE, CATEGORIES);

        assertThat(p).isEqualTo(new ParsedTransaction(23_500, java.time.LocalDate.of(2026, 11, 4), 2L, "배달의민족 주문"));
        assertThat(gemini.lastImage).isEqualTo(SMALL_JPEG);   // 작으면 줄이지 않고 원본 그대로
        assertThat(gemini.lastMime).isEqualTo("image/jpeg");
        // 영수증 프롬프트는 지출 카테고리만 보여 준다
        assertThat(gemini.systemPrompts.get(0)).contains("id=2 | 배달").doesNotContain("급여");
    }

    @Test
    @DisplayName("날짜를 못 읽으면 오늘로 채우지 않고 null (영수증은 오늘로 채우면 오히려 틀린다)")
    void doesNotFillTodayForReceipts() {
        FakeGemini gemini = new FakeGemini("""
                {"isTransaction":true,"amount":23500,"transactionDate":null,"categoryId":2,"memo":null}
                """);

        ParsedTransaction p = new GeminiReceiptOcrClient(gemini, new ReceiptPromptBuilder())
                .analyze(IMAGE, CATEGORIES);

        assertThat(p.transactionDate()).isNull();
    }

    @Test
    @DisplayName("큰 이미지는 긴 변 1568px 의 JPEG 로 줄여서 보낸다 (Django 경로는 원본 그대로)")
    void scalesDownBeforeSending() throws Exception {
        FakeGemini gemini = new FakeGemini("""
                {"isTransaction":true,"amount":23500,"transactionDate":"2026-11-04","categoryId":2,"memo":null}
                """);

        new GeminiReceiptOcrClient(gemini, new ReceiptPromptBuilder())
                .analyze(new ReceiptImage(TestImages.transparentPng(3136, 2000), "image/png"), CATEGORIES);

        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(gemini.lastImage));
        assertThat(gemini.lastMime).isEqualTo("image/jpeg");
        assertThat(Math.max(sent.getWidth(), sent.getHeight())).isEqualTo(ReceiptImageScaler.MAX_LONG_EDGE);
    }

    @Test
    @DisplayName("읽을 수 없는 파일은 AI 를 부르지 않고 415 UNSUPPORTED_FILE_TYPE")
    void unreadableFileIs415WithoutCallingAi() {
        FakeGemini gemini = new FakeGemini("{}");

        assertThatThrownBy(() -> new GeminiReceiptOcrClient(gemini, new ReceiptPromptBuilder())
                .analyze(new ReceiptImage("fake-jpeg".getBytes(), "image/jpeg"), CATEGORIES))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
        assertThat(gemini.calls).isZero();
    }
}
