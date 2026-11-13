package com.back.was.receipt;

import com.back.was.common.OcrFailedException;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Spring → Django OCR 서버 내부 API 호출 (명세 RCPT-01 처리 순서 1).
 *
 * TODO(준식님과 협의): 아래 요청·응답 모양은 임시 가정입니다.
 *   요청  POST {ocr.django-url}  multipart/form-data, 필드명 "image"
 *   응답  200 {"text": "영수증 전체 글자 (줄바꿈 포함)"}
 * Django 쪽이 CLOVA 응답의 inferText 들을 줄 단위로 이어 붙여 text 로 주면 됩니다.
 */
@Component
public class DjangoOcrClient implements OcrTextClient {

    private static final Logger log = LoggerFactory.getLogger(DjangoOcrClient.class);

    private final RestClient restClient;
    private final OcrProperties props;

    public DjangoOcrClient(@Qualifier("ocrRestClient") RestClient restClient, OcrProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    @Override
    public boolean isConfigured() {
        return props.hasDjango();
    }

    @Override
    public String extractText(byte[] image, String mimeType) {
        String ext = "image/png".equals(mimeType) ? "png" : "jpg";

        // 파일명이 있어야 multipart 에서 "파일"로 전송된다
        ByteArrayResource file = new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "receipt." + ext;
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", file);

        try {
            JsonNode res = restClient.post()
                    .uri(props.djangoUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String text = res == null ? "" : res.path("text").asString("").trim();
            if (text.isEmpty()) {
                throw new OcrFailedException("영수증에서 글자를 찾지 못했어요. 직접 입력해 주세요.");
            }
            return text;
        } catch (OcrFailedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OCR] Django 호출 실패: {}", e.getMessage());
            throw new OcrFailedException("영수증을 읽지 못했어요. 직접 입력해 주세요.", e);
        }
    }
}
