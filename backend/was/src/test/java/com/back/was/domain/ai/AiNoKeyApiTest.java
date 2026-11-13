package com.back.was.domain.ai;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gemini 키가 없을 때의 실제 동작. 서버는 뜨고, AI 기능만 503 AI_UNAVAILABLE 로 응답한다 —
 * 프론트는 이 코드를 받으면 직접 입력 화면으로 안내한다.
 *
 * <p>{@code gemini.api-key} 를 빈 값으로 못 박는다 — 개발자 PC 에 {@code GEMINI_API_KEY} 환경변수가
 * 있어도 이 테스트가 실제 Gemini 를 호출하지 않게 하기 위해서다.
 */
@SpringBootTest(properties = {"gemini.mock=false", "gemini.api-key=", "ocr.base-url="})
@AutoConfigureMockMvc
@Transactional
class AiNoKeyApiTest {

    @Autowired
    MockMvc mockMvc;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        token = AiWiringSupport.signUp(mockMvc, "ai-nokey@email.com");
    }

    @Test
    @DisplayName("키가 없으면 TX-07 은 503 AI_UNAVAILABLE")
    void parseIsUnavailable() throws Exception {
        parse(mockMvc, token, "배민 18000원")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("키가 없으면 RCPT-01(OCR 서버 없음)도 503 AI_UNAVAILABLE")
    void receiptIsUnavailable() throws Exception {
        receipt(mockMvc, token)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
    }
}
