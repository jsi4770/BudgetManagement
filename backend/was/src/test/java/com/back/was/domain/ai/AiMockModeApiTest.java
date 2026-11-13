package com.back.was.domain.ai;

import com.back.was.domain.ocr.GeminiReceiptOcrClient;
import com.back.was.domain.ocr.ReceiptOcrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.back.was.domain.ai.AiWiringSupport.parse;
import static com.back.was.domain.ai.AiWiringSupport.receipt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 빈 연결 확인(목 없이): Gemini mock 모드 + OCR 서버 미설정.
 * TX-07 은 {@link GeminiTransactionTextParser}, RCPT-01 은 {@link GeminiReceiptOcrClient} 로 흐른다.
 */
@SpringBootTest(properties = {"gemini.mock=true", "ocr.base-url="})
@AutoConfigureMockMvc
@Transactional
class AiMockModeApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TransactionTextParser parser;
    @Autowired
    ReceiptOcrClient receiptOcrClient;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        token = AiWiringSupport.signUp(mockMvc, "ai-mock@email.com");
    }

    @Test
    @DisplayName("포트 구현체가 Gemini 어댑터로 연결된다 (OCR 서버가 없으면 Gemini 직접 읽기)")
    void portsAreWired() {
        assertThat(parser).isInstanceOf(GeminiTransactionTextParser.class);
        assertThat(receiptOcrClient).isInstanceOf(GeminiReceiptOcrClient.class);
    }

    @Test
    @DisplayName("TX-07: 실제 서비스·DB 카테고리를 거쳐 200 — 모델이 모르는 카테고리는 지출 '기타'(id 6)")
    void parseWorksEndToEnd() throws Exception {
        parse(mockMvc, token, "배민 3.4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(1000))
                .andExpect(jsonPath("$.data.category.id").value(6))
                .andExpect(jsonPath("$.data.category.name").value("기타"))
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.missingFields").isEmpty());
    }

    @Test
    @DisplayName("TX-07: 모델이 파싱 실패하면 422 AI_PARSE_FAILED, 장애면 503 AI_UNAVAILABLE")
    void parseFailuresMapToSpecCodes() throws Exception {
        parse(mockMvc, token, "FAIL_422")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("AI_PARSE_FAILED"));
        parse(mockMvc, token, "FAIL_503")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("RCPT-01: 영수증은 날짜를 못 읽으면 오늘로 채우지 않고 missingFields 로 알린다")
    void receiptWorksEndToEnd() throws Exception {
        receipt(mockMvc, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(1000))
                .andExpect(jsonPath("$.data.transactionDate").doesNotExist())
                .andExpect(jsonPath("$.data.missingFields", contains("transactionDate")));
    }
}
