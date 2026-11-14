package com.back.was.domain.ai.gemini;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 Gemini 호출에서 나온 버그의 회귀 테스트. 응답 본문을 읽다가 실패하면 {@code ResourceAccessException}
 * 이 아니라 일반 {@code RestClientException} 으로 올라오는데, 그걸 못 잡아 503 이 아니라 500 이 나갔다.
 */
class HttpGeminiClientTest {

    private static final Map<String, Object> SCHEMA = GeminiSchema.object(Map.of());
    private static final String OK_BODY = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"ok\\":true}"}]}}]}""";

    private MockRestServiceServer textServer;
    private MockRestServiceServer imageServer;
    private HttpGeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder text = RestClient.builder().baseUrl("http://gemini.test");
        RestClient.Builder image = RestClient.builder().baseUrl("http://gemini-image.test");
        textServer = MockRestServiceServer.bindTo(text).build();
        imageServer = MockRestServiceServer.bindTo(image).build();
        // maxRetries=0 — 재시도 대기(1.5초)로 테스트가 느려지지 않게
        GeminiProperties props = new GeminiProperties("test-key", "m1", null, "http://x", null, null, 0, false, 0);
        client = new HttpGeminiClient(text.build(), image.build(), props, new ObjectMapper());
    }

    private static void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("응답 본문을 읽을 수 없으면 500 이 아니라 503 AI_UNAVAILABLE")
    void unreadableBodyIsUnavailable() {
        // 실제 로그: "Error while extracting response ... content type [application/octet-stream]"
        textServer.expect(requestTo("http://gemini.test/models/m1:generateContent"))
                .andRespond(withSuccess("not-json-bytes", MediaType.APPLICATION_OCTET_STREAM));

        assertUnavailable(() -> client.generateJson("sys", "hi", SCHEMA, 0.0));
    }

    @Test
    @DisplayName("연결 실패·타임아웃(I/O 오류)도 503 AI_UNAVAILABLE")
    void ioErrorIsUnavailable() {
        textServer.expect(requestTo("http://gemini.test/models/m1:generateContent"))
                .andRespond(withException(new IOException("Read timed out")));

        assertUnavailable(() -> client.generateJson("sys", "hi", SCHEMA, 0.0));
    }

    @Test
    @DisplayName("텍스트 호출은 텍스트 클라이언트로, 이미지 호출은 (타임아웃이 긴) 이미지 클라이언트로 간다")
    void imageCallsUseTheImageClient() {
        textServer.expect(requestTo("http://gemini.test/models/m1:generateContent"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        imageServer.expect(requestTo("http://gemini-image.test/models/m1:generateContent"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        JsonNode fromText = client.generateJson("sys", List.of(GeminiMessage.user("hi")), SCHEMA, 0.0);
        JsonNode fromImage = client.generateJson("sys", "영수증", new byte[]{1, 2, 3}, "image/png", SCHEMA, 0.0);

        assertThat(fromText.path("ok").asBoolean()).isTrue();
        assertThat(fromImage.path("ok").asBoolean()).isTrue();
        textServer.verify();
        imageServer.verify();
    }

    @Test
    @DisplayName("기본 타임아웃: 텍스트 10초, 이미지 30초")
    void defaultTimeouts() {
        GeminiProperties props = new GeminiProperties(null, null, null, null, null, null, null, null, null);

        assertThat(props.timeout()).hasSeconds(10);
        assertThat(props.imageTimeout()).hasSeconds(30);
    }
}
