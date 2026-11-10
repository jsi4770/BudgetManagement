package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.ai.TextParseCommand;
import com.back.was.domain.ai.TransactionTextParser;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RCPT-01 영수증 OCR 분석.
 *
 * <p><b>두 외부 의존을 모두 목으로 바꿔서</b> 실제 흐름을 검증한다 — OCR 서버도 Gemini 도
 * 아직 없지만, 둘이 붙었을 때 어떻게 동작해야 하는지는 지금 고정할 수 있다. 운영 코드에
 * 가짜 구현을 두지 않고 테스트에서만 끼우는 방식이고,
 * {@code TransactionParseApiTest}(TX-07)가 같은 방법을 쓴다.
 *
 * <p>목이 없을 때의 실제 동작(항상 502)은 {@code UnavailableReceiptOcrClientTest} 가 본다.
 *
 * <p>기대값은 명세 RCPT-01 응답 예시다: 23,500원 / 2026-11-04 / 배달 / "배달의민족 주문".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceiptOcrApiTest {

    private static final String EMAIL = "receipt@email.com";
    private static final String PASSWORD = "password123!";

    private static final long DELIVERY = 2L;

    /** 명세 RCPT-01 예시의 추출 결과. */
    private static final LocalDate RECEIPT_DATE = LocalDate.of(2026, 11, 4);

    /** OCR 이 읽었다고 가정할 텍스트. 내용은 중요하지 않다 — 파서에게 넘어가는지만 본다. */
    private static final String OCR_TEXT = "배달의민족 2026-11-04 23,500원";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReceiptOcrClient receiptOcrClient;

    @MockitoBean
    TransactionTextParser transactionTextParser;

    private String accessToken;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "홍길동", "email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = JsonPath.read(body, "$.data.accessToken");
    }

    private MockMultipartHttpServletRequestBuilder upload(MockMultipartFile image) {
        return multipart("/receipts/ocr")
                .file(image)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "receipt.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());
    }

    // --- 정상 흐름 (OCR·Gemini 가 붙었을 때) --------------------------------

    @Test
    @DisplayName("명세 RCPT-01: 이미지 → OCR → 정리 결과를 TX-07 과 같은 형식으로 준다")
    void analyzesAReceipt() throws Exception {
        given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
        given(transactionTextParser.parse(any())).willReturn(new ParsedTransaction(
                23_500, RECEIPT_DATE, DELIVERY, "배달의민족 주문"));

        mockMvc.perform(upload(jpeg()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(23_500))
                .andExpect(jsonPath("$.data.transactionDate").value("2026-11-04"))
                .andExpect(jsonPath("$.data.category.id").value(DELIVERY))
                .andExpect(jsonPath("$.data.category.name").value("배달"))
                // TX-07 과 같은 형식이라 category 에 type 이 있다 (CategoryResponse)
                .andExpect(jsonPath("$.data.category.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.memo").value("배달의민족 주문"))
                .andExpect(jsonPath("$.data.missingFields", Matchers.empty()));
    }

    @Test
    @DisplayName("OCR 이 읽은 텍스트가 그대로 파서로 넘어간다 — 두 단계가 이어져 있다")
    void passesTheOcrTextToTheParser() throws Exception {
        given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
        given(transactionTextParser.parse(any())).willReturn(new ParsedTransaction(
                23_500, RECEIPT_DATE, DELIVERY, null));

        mockMvc.perform(upload(jpeg())).andExpect(status().isOk());

        ArgumentCaptor<TextParseCommand> command = ArgumentCaptor.forClass(TextParseCommand.class);
        then(transactionTextParser).should().parse(command.capture());
        assertThat(command.getValue().text()).isEqualTo(OCR_TEXT);
        // 카테고리 목록도 함께 넘어간다 — 모델이 DB 에 있는 것 중에서 골라야 한다
        assertThat(command.getValue().categories()).isNotEmpty();
    }

    @Test
    @DisplayName("업로드한 바이트와 타입이 OCR 포트로 넘어간다 — 어댑터가 쓸 값이다")
    void passesTheImageToTheOcrPort() throws Exception {
        given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
        given(transactionTextParser.parse(any())).willReturn(new ParsedTransaction(
                23_500, RECEIPT_DATE, DELIVERY, null));

        mockMvc.perform(upload(jpeg())).andExpect(status().isOk());

        ArgumentCaptor<ReceiptImage> image = ArgumentCaptor.forClass(ReceiptImage.class);
        then(receiptOcrClient).should().extractText(image.capture());
        assertThat(image.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(image.getValue().bytes()).isEqualTo("fake-jpeg-bytes".getBytes());
    }

    @Test
    @DisplayName("일부만 추출되면 missingFields 로 알린다 — TX-07 과 같은 규칙이다")
    void partialExtractionIsNormal() throws Exception {
        given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
        // 날짜를 못 읽은 영수증 (구겨진 사진 등)
        given(transactionTextParser.parse(any())).willReturn(new ParsedTransaction(
                23_500, null, DELIVERY, null));

        mockMvc.perform(upload(jpeg()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionDate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.missingFields", Matchers.contains("transactionDate")));
    }

    @Test
    @DisplayName("png 도 받는다 (명세 RCPT-01: jpg / png)")
    void acceptsPng() throws Exception {
        given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
        given(transactionTextParser.parse(any())).willReturn(new ParsedTransaction(
                23_500, RECEIPT_DATE, DELIVERY, null));

        mockMvc.perform(upload(new MockMultipartFile(
                        "image", "receipt.png", "image/png", "fake-png".getBytes())))
                .andExpect(status().isOk());
    }

    // --- 실패 (명세 RCPT-01 Errors) -----------------------------------------

    @Nested
    @DisplayName("실패")
    class Failures {

        @Test
        @DisplayName("OCR 이 실패하면 502 OCR_FAILED — 파서는 부르지 않는다")
        void ocrFailureIsBadGateway() throws Exception {
            willThrow(new BusinessException(ErrorCode.OCR_FAILED))
                    .given(receiptOcrClient).extractText(any());

            mockMvc.perform(upload(jpeg()))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("OCR_FAILED"));

            then(transactionTextParser).should(never()).parse(any());
        }

        @Test
        @DisplayName("OCR 이 글자를 하나도 못 읽으면 422 가 아니라 502 다 — 고쳐야 할 건 사진이다")
        void blankTextIsAlsoBadGateway() throws Exception {
            given(receiptOcrClient.extractText(any())).willReturn("   ");

            mockMvc.perform(upload(jpeg()))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("OCR_FAILED"));

            then(transactionTextParser).should(never()).parse(any());
        }

        @Test
        @DisplayName("정리 단계가 실패하면 503 AI_UNAVAILABLE (OCR 은 성공했다)")
        void parserFailureIsServiceUnavailable() throws Exception {
            given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
            willThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE))
                    .given(transactionTextParser).parse(any());

            mockMvc.perform(upload(jpeg()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
        }

        @Test
        @DisplayName("셋 다 못 뽑으면 422 AI_PARSE_FAILED — TX-07 과 같은 조건이다")
        void nothingExtractedIsUnprocessable() throws Exception {
            given(receiptOcrClient.extractText(any())).willReturn(OCR_TEXT);
            given(transactionTextParser.parse(any()))
                    .willReturn(new ParsedTransaction(null, null, null, null));

            mockMvc.perform(upload(jpeg()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("AI_PARSE_FAILED"));
        }

        @Test
        @DisplayName("jpg·png 가 아니면 415 UNSUPPORTED_FILE_TYPE — HEIC 는 프론트가 변환한다")
        void otherFormatsAreRejected() throws Exception {
            mockMvc.perform(upload(new MockMultipartFile(
                            "image", "receipt.heic", "image/heic", "fake-heic".getBytes())))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));

            then(receiptOcrClient).should(never()).extractText(any());
        }

        @Test
        @DisplayName("빈 파일도 415 — 0바이트를 OCR 서버에 보내 봐야 실패한다")
        void emptyFileIsRejected() throws Exception {
            mockMvc.perform(upload(new MockMultipartFile(
                            "image", "receipt.jpg", "image/jpeg", new byte[0])))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));
        }

        @Test
        @DisplayName("image 파트가 없으면 400 — 500 이 아니다")
        void missingPartIsBadRequest() throws Exception {
            mockMvc.perform(multipart("/receipts/ocr")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("image"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
        }

        @Test
        @DisplayName("multipart 가 아니면 415 — consumes 가 걸러낸다")
        void nonMultipartIsRejected() throws Exception {
            mockMvc.perform(post("/receipts/ocr")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("인증 없이 호출하면 401 UNAUTHORIZED")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(multipart("/receipts/ocr").file(jpeg()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
