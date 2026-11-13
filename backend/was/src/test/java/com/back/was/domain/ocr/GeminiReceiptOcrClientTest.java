package com.back.was.domain.ocr;

import com.back.was.domain.ai.AiTestFixtures.FakeGemini;
import com.back.was.domain.ai.ParsedTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;

/** OCR 서버가 없을 때(ocr.base-url 비어 있음)의 대체 경로: Gemini 가 이미지를 직접 읽는다. */
class GeminiReceiptOcrClientTest {

    private static final ReceiptImage IMAGE = new ReceiptImage("fake-jpeg".getBytes(), "image/jpeg");

    @Test
    @DisplayName("이미지 바이트·타입이 Gemini 로 넘어가고 결과가 ParsedTransaction 이 된다")
    void readsImageDirectly() {
        FakeGemini gemini = new FakeGemini("""
                {"isTransaction":true,"amount":23500,"transactionDate":"2026-11-04","categoryId":2,"memo":"배달의민족 주문"}
                """);

        ParsedTransaction p = new GeminiReceiptOcrClient(gemini, new ReceiptPromptBuilder())
                .analyze(IMAGE, CATEGORIES);

        assertThat(p).isEqualTo(new ParsedTransaction(23_500, java.time.LocalDate.of(2026, 11, 4), 2L, "배달의민족 주문"));
        assertThat(gemini.lastImage).isEqualTo("fake-jpeg".getBytes());
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
}
