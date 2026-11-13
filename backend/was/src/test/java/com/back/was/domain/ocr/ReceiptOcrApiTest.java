package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.category.dto.CategoryResponse;
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
import java.util.List;

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
 * <p>분석기({@link ReceiptOcrClient})를 목으로 바꿔서 <b>백엔드 책임만</b> 검증한다 — 이미지 검사,
 * 카테고리 목록 전달, TX-07 과 같은 응답 조립, 실패 코드. 실제 Django·Gemini 호출은
 * {@code DjangoReceiptOcrClientTest}·{@code GeminiReceiptOcrClientTest} 가 본다.
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

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReceiptOcrClient receiptOcrClient;

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
        given(receiptOcrClient.analyze(any(), any())).willReturn(new ParsedTransaction(
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
    @DisplayName("DB 카테고리 목록이 분석기로 넘어간다 — 모델이 실제 id 중에서 골라야 한다")
    @SuppressWarnings("unchecked")
    void passesCategoriesToTheClient() throws Exception {
        given(receiptOcrClient.analyze(any(), any())).willReturn(new ParsedTransaction(
                23_500, RECEIPT_DATE, DELIVERY, null));

        mockMvc.perform(upload(jpeg())).andExpect(status().isOk());

        ArgumentCaptor<List<CategoryResponse>> categories = ArgumentCaptor.forClass(List.class);
        then(receiptOcrClient).should().analyze(any(), categories.capture());
        assertThat(categories.getValue()).isNotEmpty();
        assertThat(categories.getValue()).anySatisfy(c -> {
            assertThat(c.id()).isEqualTo(DELIVERY);
            assertThat(c.name()).isEqualTo("배달");
        });
    }

    @Test
    @DisplayName("업로드한 바이트와 타입이 OCR 포트로 넘어간다 — 어댑터가 쓸 값이다")
    void passesTheImageToTheOcrPort() throws Exception {
        given(receiptOcrClient.analyze(any(), any())).willReturn(new ParsedTransaction(
                23_500, RECEIPT_DATE, DELIVERY, null));

        mockMvc.perform(upload(jpeg())).andExpect(status().isOk());

        ArgumentCaptor<ReceiptImage> image = ArgumentCaptor.forClass(ReceiptImage.class);
        then(receiptOcrClient).should().analyze(image.capture(), any());
        assertThat(image.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(image.getValue().bytes()).isEqualTo("fake-jpeg-bytes".getBytes());
    }

    @Test
    @DisplayName("일부만 추출되면 missingFields 로 알린다 — TX-07 과 같은 규칙이다")
    void partialExtractionIsNormal() throws Exception {
        // 날짜를 못 읽은 영수증 (구겨진 사진 등)
        given(receiptOcrClient.analyze(any(), any())).willReturn(new ParsedTransaction(
                23_500, null, DELIVERY, null));

        mockMvc.perform(upload(jpeg()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionDate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.missingFields", Matchers.contains("transactionDate")));
    }

    @Test
    @DisplayName("png 도 받는다 (명세 RCPT-01: jpg / png)")
    void acceptsPng() throws Exception {
        given(receiptOcrClient.analyze(any(), any())).willReturn(new ParsedTransaction(
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
        @DisplayName("OCR 이 실패하면 502 OCR_FAILED")
        void ocrFailureIsBadGateway() throws Exception {
            willThrow(new BusinessException(ErrorCode.OCR_FAILED))
                    .given(receiptOcrClient).analyze(any(), any());

            mockMvc.perform(upload(jpeg()))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("OCR_FAILED"));
        }

        @Test
        @DisplayName("AI 가 막히면 503 AI_UNAVAILABLE — OCR 실패(502)와 코드로 구분된다")
        void aiFailureIsServiceUnavailable() throws Exception {
            willThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE))
                    .given(receiptOcrClient).analyze(any(), any());

            mockMvc.perform(upload(jpeg()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
        }

        @Test
        @DisplayName("셋 다 못 뽑으면 422 AI_PARSE_FAILED — TX-07 과 같은 조건이다")
        void nothingExtractedIsUnprocessable() throws Exception {
            given(receiptOcrClient.analyze(any(), any()))
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

            then(receiptOcrClient).should(never()).analyze(any(), any());
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
