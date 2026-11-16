package com.back.was.ai.claude;

import com.back.was.ai.gemini.GeminiMessage;
import com.back.was.ai.gemini.GeminiSchema;
import com.back.was.common.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ClaudeClient} 를 가짜 Messages API 서버(JDK HttpServer)에 붙여, 요청 모양과 실패 처리를 확인한다.
 * 실제 API 는 부르지 않는다.
 */
class ClaudeClientTest {

    private static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> props = new LinkedHashMap<>();
        props.put("amount", GeminiSchema.nullable(GeminiSchema.integer("금액")));
        props.put("memo", GeminiSchema.string("메모"));
        SCHEMA = GeminiSchema.object(props);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    /** 받은 요청 (모델 · 본문 JSON · 헤더) */
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    /** 차례대로 돌려줄 응답. 비면 마지막 것을 반복 */
    private final Deque<Reply> replies = new ArrayDeque<>();

    record Received(String model, JsonNode body, String authorization, String version) {}

    record Reply(int status, String body, long delayMillis) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        JsonNode body = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
        received.add(new Received(body.path("model").asString(), body,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("anthropic-version")));
        Reply reply;
        synchronized (replies) {
            reply = replies.size() > 1 ? replies.poll() : replies.peek();
        }
        if (reply.delayMillis() > 0) {
            try {
                Thread.sleep(reply.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // 클라이언트가 타임아웃으로 먼저 끊은 경우
        } finally {
            exchange.close();
        }
    }

    private void reply(int status, String body) {
        replies.add(new Reply(status, body, 0));
    }

    private static String message(String model, String text, String stopReason) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"%s",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":1,"output_tokens":1}}"""
                .formatted(model, new ObjectMapper().writeValueAsString(text), stopReason);
    }

    private static String error(String type) {
        return """
                {"type":"error","error":{"type":"%s","message":"x"}}""".formatted(type);
    }

    private ClaudeClient client(String fallbackModel, int maxRetries, Duration timeout) {
        ClaudeProperties props = new ClaudeProperties("test-key", "m1", fallbackModel,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                null, null, timeout, null, maxRetries, false, 0);
        return new ClaudeClient(props, objectMapper);
    }

    private ClaudeClient client() {
        return client(null, 0, null);
    }

    private static void assertError(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("요청: 인증·버전 헤더, temperature 없음, 구조화 출력 스키마는 표준 JSON Schema 로 변환")
    void requestShape() {
        reply(200, message("m1", "{\"amount\":12000,\"memo\":\"점심\"}", "end_turn"));

        JsonNode result = client().generateJson("sys", List.of(GeminiMessage.user("hi"), GeminiMessage.model("ok"),
                GeminiMessage.user("again")), SCHEMA, 0.7);

        assertThat(result.path("amount").asInt()).isEqualTo(12000);
        Received r = received.get(0);
        assertThat(r.authorization()).isEqualTo("Bearer test-key");
        assertThat(r.version()).isNotBlank();
        JsonNode body = r.body();
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.path("system").asString()).isEqualTo("sys");
        assertThat(body.path("messages").findValuesAsString("role")).containsExactly("user", "assistant", "user");
        assertThat(body.path("output_config").path("effort").asString()).isEqualTo("low");

        JsonNode schema = body.path("output_config").path("format").path("schema");
        assertThat(body.path("output_config").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(schema.path("type").asString()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.has("propertyOrdering")).isFalse();
        assertThat(schema.path("properties").path("memo").path("type").asString()).isEqualTo("string");
        JsonNode amount = schema.path("properties").path("amount");
        assertThat(amount.path("anyOf").path(0).path("type").asString()).isEqualTo("integer");
        assertThat(amount.path("anyOf").path(1).path("type").asString()).isEqualTo("null");
        assertThat(amount.has("nullable")).isFalse();
    }

    @Test
    @DisplayName("이미지는 base64 image 블록으로, 텍스트 앞에 보낸다")
    void imageRequest() {
        reply(200, message("m1", "{\"amount\":1,\"memo\":\"m\"}", "end_turn"));

        client().generateJson("sys", "read", new byte[]{1, 2, 3}, "image/png", SCHEMA, 0.0);

        JsonNode content = received.get(0).body().path("messages").path(0).path("content");
        assertThat(content.path(0).path("type").asString()).isEqualTo("image");
        assertThat(content.path(0).path("source").path("media_type").asString()).isEqualTo("image/png");
        assertThat(content.path(0).path("source").path("data").asString()).isEqualTo("AQID");
        assertThat(content.path(1).path("text").asString()).isEqualTo("read");
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않고 503")
    void noKeyIsUnavailable() {
        ClaudeProperties props = new ClaudeProperties("", "m1", null, null, null, null, null, null, 0, false, 0);
        assertError(() -> new ClaudeClient(props, objectMapper).generateJson("s", "u", SCHEMA, 0), "AI_UNAVAILABLE");
    }

    @Test
    @DisplayName("응답 본문을 읽을 수 없으면 500 이 아니라 503")
    void unreadableBodyIsUnavailable() {
        reply(200, "not-json");
        assertError(() -> client().generateJson("s", "u", SCHEMA, 0), "AI_UNAVAILABLE");
    }

    @Test
    @DisplayName("400 은 설정 문제라 재시도·예비 모델 없이 503")
    void badRequestIsNotRetried() {
        reply(400, error("invalid_request_error"));
        assertError(() -> client("m2", 1, null).generateJson("s", "u", SCHEMA, 0), "AI_UNAVAILABLE");
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("429 는 같은 모델을 재시도하지 않고 바로 예비 모델로")
    void rateLimitGoesToFallback() {
        reply(429, error("rate_limit_error"));
        reply(200, message("m2", "{\"amount\":1,\"memo\":\"m\"}", "end_turn"));

        client("m2", 1, null).generateJson("s", "u", SCHEMA, 0);

        assertThat(received).extracting(Received::model).containsExactly("m1", "m2");
    }

    @Test
    @DisplayName("529(과부하)는 같은 모델로 재시도한다")
    void overloadedIsRetried() {
        reply(529, error("overloaded_error"));
        reply(200, message("m1", "{\"amount\":1,\"memo\":\"m\"}", "end_turn"));

        client(null, 1, null).generateJson("s", "u", SCHEMA, 0);

        assertThat(received).extracting(Received::model).containsExactly("m1", "m1");
    }

    @Test
    @DisplayName("거절(refusal)·토큰 상한(max_tokens)이면 422")
    void unusableStopReasonIsParseFailed() {
        reply(200, message("m1", "", "refusal"));
        assertError(() -> client().generateJson("s", "u", SCHEMA, 0), "AI_PARSE_FAILED");

        replies.clear();
        reply(200, message("m1", "{\"amount\":", "max_tokens"));
        assertError(() -> client().generateJson("s", "u", SCHEMA, 0), "AI_PARSE_FAILED");
    }

    @Test
    @DisplayName("응답이 느리면 재시도·예비 모델까지 포함해 timeout 안에 503")
    void wholeCallStaysWithinTimeout() {
        replies.add(new Reply(200, message("m1", "{}", "end_turn"), 3_000));
        Duration limit = Duration.ofMillis(800);

        Instant start = Instant.now();
        assertError(() -> client("m2", 2, limit).generateJson("s", "u", SCHEMA, 0), "AI_UNAVAILABLE");

        // 시도마다 800ms 를 새로 주면 (2 모델 × 3 시도) 훨씬 넘는다
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(2_000));
    }
}
