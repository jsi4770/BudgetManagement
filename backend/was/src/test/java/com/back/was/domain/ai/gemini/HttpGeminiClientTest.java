package com.back.was.domain.ai.gemini;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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

    // --- 호출 전체의 시간 상한 (GeminiDeadline) -----------------------------------------------

    /** 기본 m1 · 예비 m2 · 재시도 1회 — 운영 기본값과 같은 모양에 상한만 바꾼 클라이언트. */
    private HttpGeminiClient clientWithLimit(Duration limit) {
        RestClient.Builder text = RestClient.builder().baseUrl("http://gemini.test");
        textServer = MockRestServiceServer.bindTo(text).build();
        GeminiProperties props = new GeminiProperties("test-key", "m1", "m2", "http://x", limit, null, 1, false, 0);
        return new HttpGeminiClient(text.build(), RestClient.builder().build(), props, new ObjectMapper());
    }

    @Test
    @DisplayName("상한을 넘기면 남은 재시도와 예비 모델을 건너뛰고 바로 503 — 최악 55초가 되던 버그")
    void deadlineStopsRetriesAndFallback() {
        HttpGeminiClient limited = clientWithLimit(Duration.ofMillis(200));
        // 한 번의 시도가 상한을 다 써 버린다. 두 번째 요청이 오면 MockRestServiceServer 가 AssertionError 를 던진다
        textServer.expect(once(), requestTo("http://gemini.test/models/m1:generateContent"))
                .andRespond(request -> {
                    sleepQuietly(300);
                    throw new IOException("Read timed out");
                });

        long started = System.nanoTime();
        assertUnavailable(() -> limited.generateJson("sys", "hi", SCHEMA, 0.0));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        textServer.verify();
        // 재시도 대기(1.5초)도 하지 않았다
        assertThat(elapsedMillis).isLessThan(1_000);
    }

    @Test
    @DisplayName("상한 안이면 429 를 받고 예비 모델로 넘어가는 동작은 그대로다")
    void fallbackStillWorksWithinTheLimit() {
        HttpGeminiClient limited = clientWithLimit(Duration.ofSeconds(10));
        textServer.expect(requestTo("http://gemini.test/models/m1:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        textServer.expect(requestTo("http://gemini.test/models/m2:generateContent"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        JsonNode result = limited.generateJson("sys", "hi", SCHEMA, 0.0);

        assertThat(result.path("ok").asBoolean()).isTrue();
        textServer.verify();
    }

    @Test
    @DisplayName("시도 하나의 연결·읽기 타임아웃도 남은 시간으로 줄어든다 — 마감 직전에 시작한 시도가 10초를 더 쓰지 않게")
    void requestFactoryClipsTimeoutsToTheDeadline() throws IOException {
        GeminiDeadline.RequestFactory factory = new GeminiDeadline.RequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);

        HttpURLConnection clipped = connection();
        GeminiDeadline.within(Instant.now().plusSeconds(2), () -> {
            prepare(factory, clipped);
            return null;
        });
        assertThat(clipped.getConnectTimeout()).isBetween(1, 2_000);
        assertThat(clipped.getReadTimeout()).isBetween(1, 2_000);

        // 상한 밖(within 이 끝난 뒤)에서는 설정값 그대로 — 스레드에 마감이 남지 않는다
        HttpURLConnection unclipped = connection();
        prepare(factory, unclipped);
        assertThat(unclipped.getConnectTimeout()).isEqualTo(3_000);
        assertThat(unclipped.getReadTimeout()).isEqualTo(10_000);

        // 이미 지난 마감이면 연결을 열지 않는다
        assertThatThrownBy(() -> GeminiDeadline.within(Instant.now().minusMillis(1), () -> {
            prepare(factory, connection());
            return null;
        })).hasCauseInstanceOf(SocketTimeoutException.class);
    }

    private static HttpURLConnection connection() {
        try {
            // openConnection 은 연결하지 않는다 — 설정만 확인한다
            return (HttpURLConnection) URI.create("http://localhost:1/").toURL().openConnection();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void prepare(GeminiDeadline.RequestFactory factory, HttpURLConnection connection) {
        try {
            factory.prepareConnection(connection, "POST");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
