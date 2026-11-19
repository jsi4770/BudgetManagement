package com.back.was.domain.ocr;

import com.back.was.domain.ai.AiTestFixtures.FakeLlm;
import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OCR 서버가 없을 때(ocr.base-url 비어 있음)의 대체 경로: Claude 가 이미지를 직접 읽는다. */
class LlmReceiptOcrClientTest {

    private static final byte[] SMALL_JPEG = TestImages.jpeg(400, 600);
    private static final ReceiptImage IMAGE = new ReceiptImage(SMALL_JPEG, "image/jpeg");

    @Test
    @DisplayName("이미지 바이트·타입이 Claude 로 넘어가고 결과가 ParsedTransaction 목록이 된다")
    void readsImageDirectly() {
        FakeLlm llm = new FakeLlm("""
                {"items":[{"amount":23500,"transactionDate":"2026-11-04","categoryId":2,"memo":"배달의민족 주문"}]}
                """);

        List<ParsedTransaction> p = new LlmReceiptOcrClient(llm, new ReceiptPromptBuilder())
                .analyze(IMAGE, CATEGORIES);

        assertThat(p).containsExactly(new ParsedTransaction(23_500, LocalDate.of(2026, 11, 4), 2L, "배달의민족 주문"));
        assertThat(llm.lastImage).isEqualTo(SMALL_JPEG);   // 작으면 줄이지 않고 원본 그대로
        assertThat(llm.lastMime).isEqualTo("image/jpeg");
        // 금융 앱 캡처의 입금도 읽어야 해서 수입 카테고리까지 보여 준다
        assertThat(llm.systemPrompts.get(0)).contains("id=2 | 배달 (지출)").contains("급여 (수입)");
    }

    @Test
    @DisplayName("금융 앱 캡처처럼 여러 건이면 모두, 받은 순서대로 돌려준다")
    void readsSeveralItems() {
        FakeLlm llm = new FakeLlm("""
                {"items":[
                  {"amount":500000,"transactionDate":"2026-11-18","categoryId":8,"memo":"부모님 송금"},
                  {"amount":4500,"transactionDate":"2026-11-18","categoryId":3,"memo":"스타벅스"}]}
                """);

        List<ParsedTransaction> p = new LlmReceiptOcrClient(llm, new ReceiptPromptBuilder())
                .analyze(IMAGE, CATEGORIES);

        assertThat(p).containsExactly(
                new ParsedTransaction(500_000, LocalDate.of(2026, 11, 18), 8L, "부모님 송금"),
                new ParsedTransaction(4_500, LocalDate.of(2026, 11, 18), 3L, "스타벅스"));
    }

    @Test
    @DisplayName("거래를 못 찾으면(영수증이 아님) 빈 목록 — 서비스가 422 로 바꾼다")
    void noItemsIsEmptyList() {
        FakeLlm llm = new FakeLlm("""
                {"items":[]}
                """);

        assertThat(new LlmReceiptOcrClient(llm, new ReceiptPromptBuilder()).analyze(IMAGE, CATEGORIES)).isEmpty();
    }

    @Test
    @DisplayName("날짜를 못 읽으면 오늘로 채우지 않고 null (영수증은 오늘로 채우면 오히려 틀린다)")
    void doesNotFillTodayForReceipts() {
        FakeLlm llm = new FakeLlm("""
                {"items":[{"amount":23500,"transactionDate":null,"categoryId":2,"memo":null}]}
                """);

        List<ParsedTransaction> p = new LlmReceiptOcrClient(llm, new ReceiptPromptBuilder())
                .analyze(IMAGE, CATEGORIES);

        assertThat(p.get(0).transactionDate()).isNull();
    }

    @Test
    @DisplayName("큰 이미지는 긴 변 1568px 의 JPEG 로 줄여서 보낸다 (Django 경로는 원본 그대로)")
    void scalesDownBeforeSending() throws Exception {
        FakeLlm llm = new FakeLlm("""
                {"items":[{"amount":23500,"transactionDate":"2026-11-04","categoryId":2,"memo":null}]}
                """);

        new LlmReceiptOcrClient(llm, new ReceiptPromptBuilder())
                .analyze(new ReceiptImage(TestImages.transparentPng(3136, 2000), "image/png"), CATEGORIES);

        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(llm.lastImage));
        assertThat(llm.lastMime).isEqualTo("image/jpeg");
        assertThat(Math.max(sent.getWidth(), sent.getHeight())).isEqualTo(ReceiptImageScaler.MAX_LONG_EDGE);
    }

    @Test
    @DisplayName("읽을 수 없는 파일은 AI 를 부르지 않고 415 UNSUPPORTED_FILE_TYPE")
    void unreadableFileIs415WithoutCallingAi() {
        FakeLlm llm = new FakeLlm("{}");

        assertThatThrownBy(() -> new LlmReceiptOcrClient(llm, new ReceiptPromptBuilder())
                .analyze(new ReceiptImage("fake-jpeg".getBytes(), "image/jpeg"), CATEGORIES))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
        assertThat(llm.calls).isZero();
    }
}
