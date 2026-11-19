package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Django OCR 서버(backend/ocr)와의 계약. 실제 네트워크 없이 응답을 흉내 낸다. */
class DjangoReceiptOcrClientTest {

    private static final ReceiptImage IMAGE = new ReceiptImage("fake-jpeg".getBytes(), "image/jpeg");

    private MockRestServiceServer server;
    private DjangoReceiptOcrClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ocr.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DjangoReceiptOcrClient(builder.build(), new ObjectMapper());
    }

    @Test
    @DisplayName("Django 응답(backend/ocr README 예시)을 ParsedTransaction 으로 바꾼다")
    void mapsDjangoResponse() {
        server.expect(requestTo("http://ocr.test/receipts/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"amount":23500,"transactionDate":"2026-11-04",
                         "category":{"id":2,"name":"배달","type":"EXPENSE"},
                         "memo":"배달의민족 주문","missingFields":[]}}
                        """, MediaType.APPLICATION_JSON));

        ParsedTransaction p = client.analyze(IMAGE, CATEGORIES);

        assertThat(p).isEqualTo(new ParsedTransaction(23_500, LocalDate.of(2026, 11, 4), 2L, "배달의민족 주문"));
        server.verify();
    }

    @Test
    @DisplayName("요청에 이미지와 DB 카테고리 JSON 이 함께 실린다 — Django 기본 목록의 하드코딩 id 에 기대지 않는다")
    void sendsImageAndCategories() {
        server.expect(requestTo("http://ocr.test/receipts/ocr"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"image\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"categories\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"배달\"")))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"amount":1000,"transactionDate":null,"category":null,"memo":null,
                         "missingFields":["transactionDate","category","memo"]}}
                        """, MediaType.APPLICATION_JSON));

        client.analyze(IMAGE, CATEGORIES);

        server.verify();
    }

    @Test
    @DisplayName("Django 가 준 missingFields 는 쓰지 않는다 — 못 읽은 값은 null 로 남는다")
    void unreadableFieldsStayNull() {
        server.expect(requestTo("http://ocr.test/receipts/ocr"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"amount":null,"transactionDate":"날짜없음","category":{"id":2,"name":"배달","type":"EXPENSE"},
                         "memo":"   ","missingFields":["amount","transactionDate","memo"]}}
                        """, MediaType.APPLICATION_JSON));

        ParsedTransaction p = client.analyze(IMAGE, CATEGORIES);

        assertThat(p).isEqualTo(new ParsedTransaction(null, null, 2L, null));
    }

    @Test
    @DisplayName("Django 가 503(AI 장애)이면 503 AI_UNAVAILABLE — OCR 장애(502)와 구분한다")
    void django503IsAiUnavailable() {
        server.expect(requestTo("http://ocr.test/receipts/ocr"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"error\":{\"code\":\"AI_UNAVAILABLE\"}}"));

        assertThatThrownBy(() -> client.analyze(IMAGE, CATEGORIES))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("그 밖의 Django 오류(500 등)는 502 OCR_FAILED")
    void otherErrorsAreOcrFailed() {
        server.expect(requestTo("http://ocr.test/receipts/ocr"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.analyze(IMAGE, CATEGORIES))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OCR_FAILED));
    }

    @Test
    @DisplayName("계약과 다른 응답(success=false, data 없음)은 502 OCR_FAILED")
    void malformedResponseIsOcrFailed() {
        server.expect(requestTo("http://ocr.test/receipts/ocr"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyze(IMAGE, CATEGORIES))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OCR_FAILED));
    }
}
