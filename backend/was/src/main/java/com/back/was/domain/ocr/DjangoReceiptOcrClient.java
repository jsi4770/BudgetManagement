package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Django OCR 서버({@code backend/ocr}) 호출. {@code POST {ocr.base-url}/receipts/ocr}.
 *
 * <p>요청은 {@code multipart/form-data} — {@code image}(파일)와 {@code categories}(DB 카테고리 JSON).
 * 카테고리를 넘겨야 Django 의 기본 목록(하드코딩된 id)이 아니라 <b>실제 DB id</b> 로 골라 온다.
 * 응답은 {@code {"success":true,"data":{amount,transactionDate,category{id,name,type},memo,missingFields}}}.
 *
 * <p>Django 가 준 {@code missingFields} 는 쓰지 않는다 — Django 는 {@code memo} 까지 필수로 세지만
 * 명세 TX-07/RCPT-01 의 필수는 금액·날짜·카테고리 셋이다. {@code null} 로부터 서비스가 다시 만든다.
 *
 * <p><b>이미지·응답 원문을 로그에 남기지 않는다.</b>
 */
@Component
@ConditionalOnExpression(OcrConfig.DJANGO_CONFIGURED)
public class DjangoReceiptOcrClient implements ReceiptOcrClient {

    private static final Logger log = LoggerFactory.getLogger(DjangoReceiptOcrClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DjangoReceiptOcrClient(@Qualifier("ocrRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedTransaction analyze(ReceiptImage image, List<CategoryResponse> categories) {
        // MultipartBodyBuilder 는 쓰지 않는다 — org.reactivestreams 가 필요한데 이 프로젝트는 MVC 만 쓴다.
        String filename = "png".equals(extensionOf(image.contentType())) ? "receipt.png" : "receipt.jpg";
        // 파일명이 있어야 multipart 에서 "파일"로 전송된다
        ByteArrayResource file = new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(image.contentType()));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", new HttpEntity<>(file, fileHeaders));
        form.add("categories", categoriesJson(categories));

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/receipts/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Django 가 503 을 주면 그쪽 AI(Vertex)가 막힌 것이다 — 명세상 503 AI_UNAVAILABLE 로 구분해 준다.
            log.warn("[RCPT-01] OCR 서버 HTTP {}", e.getStatusCode().value());
            throw e.getStatusCode().value() == 503
                    ? new BusinessException(ErrorCode.AI_UNAVAILABLE)
                    : new BusinessException(ErrorCode.OCR_FAILED);
        } catch (RestClientException e) {
            // 연결 실패·타임아웃. 원인 클래스만 남긴다.
            log.warn("[RCPT-01] OCR 서버 호출 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OCR_FAILED);
        }

        return toParsed(response);
    }

    private ParsedTransaction toParsed(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false) || !response.path("data").isObject()) {
            log.warn("[RCPT-01] OCR 서버 응답 형식이 계약과 다릅니다");
            throw new BusinessException(ErrorCode.OCR_FAILED);
        }
        JsonNode data = response.path("data");

        Integer amount = null;
        JsonNode a = data.path("amount");
        if (a.isIntegralNumber() && a.asLong() >= 1 && a.asLong() <= Integer.MAX_VALUE) {
            amount = (int) a.asLong();
        }

        LocalDate date = null;
        String rawDate = data.path("transactionDate").asString(null);
        if (rawDate != null && !rawDate.isBlank()) {
            try {
                date = LocalDate.parse(rawDate.trim());
            } catch (DateTimeParseException ignored) {
                date = null;
            }
        }

        JsonNode id = data.path("category").path("id");
        Long categoryId = id.isIntegralNumber() ? id.asLong() : null;

        String memo = data.path("memo").isString() ? data.path("memo").asString().strip() : null;
        if (memo != null) {
            memo = memo.length() > 255 ? memo.substring(0, 255) : memo;
            memo = memo.isEmpty() ? null : memo;
        }

        return new ParsedTransaction(amount, date, categoryId, memo);
    }

    private String categoriesJson(List<CategoryResponse> categories) {
        return objectMapper.writeValueAsString(categories.stream()
                .map(c -> Map.of("id", c.id(), "name", c.name(), "type", c.type()))
                .toList());
    }

    private static String extensionOf(String contentType) {
        return "image/png".equalsIgnoreCase(contentType) ? "png" : "jpg";
    }
}
