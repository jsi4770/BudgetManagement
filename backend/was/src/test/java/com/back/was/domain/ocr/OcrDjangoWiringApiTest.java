package com.back.was.domain.ocr;

import com.back.was.domain.ai.AiWiringSupportAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code ocr.base-url} 이 있으면 Django 클라이언트가 선택된다. 주소가 닿지 않을 때(연결 거부)
 * 명세대로 502 OCR_FAILED 가 되는지 실제 HTTP 클라이언트로 확인한다.
 */
@SpringBootTest(properties = {"gemini.mock=true", "ocr.base-url=http://127.0.0.1:1", "ocr.timeout=2s"})
@AutoConfigureMockMvc
@Transactional
class OcrDjangoWiringApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ReceiptOcrClient receiptOcrClient;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        token = AiWiringSupportAccess.signUp(mockMvc, "ocr-django@email.com");
    }

    @Test
    @DisplayName("ocr.base-url 이 있으면 Django 클라이언트가 연결된다")
    void djangoClientIsWired() {
        assertThat(receiptOcrClient).isInstanceOf(DjangoReceiptOcrClient.class);
    }

    @Test
    @DisplayName("OCR 서버에 연결할 수 없으면 502 OCR_FAILED (Gemini 로 몰래 우회하지 않는다)")
    void unreachableOcrServerIsBadGateway() throws Exception {
        AiWiringSupportAccess.receipt(mockMvc, token)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("OCR_FAILED"));
    }
}
