package com.back.was.domain.ai.llm;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 SDK 로 로컬 HTTP 서버(Messages API 흉내)를 부른다. 확인하는 것: 요청 모양(구조화 출력·effort·temperature
 * 없음·이미지 블록), 실패 → 503/422 변환, 429 → 예비 모델, 호출 전체 시간 상한.
 */
class ClaudeLlmClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> SCHEMA = LlmSchema.object(Map.of("ok", LlmSchema.bool(null)));

    private HttpServer server;
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
    private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
    /** 키가 실린 헤더 — SDK 3.2.0 은 {@code Authorization: Bearer} 로 보낸다(이전 버전은 {@code x-api-key}). */
    private final List<String> credentials = Collections.synchronizedList(new ArrayList<>());

    private record Reply(int status, String body, long delayMillis) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            byte[] in = exchange.getRequestBody().readAllBytes();
            requests.add(JSON.readTree(new String(in, StandardCharsets.UTF_8)));
            String apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
            credentials.add(apiKey != null ? apiKey : exchange.getRequestHeaders().getFirst("Authorization"));
            Reply reply = replies.poll();
            if (reply == null) {
                reply = new Reply(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"no script\"}}", 0);
            }
            sleepQuietly(reply.delayMillis());
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(reply.status(), out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            } catch (IOException ignored) {
                // 클라이언트가 타임아웃으로 먼저 끊은 경우
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 기본 m1 · 예비 fallback · 재시도 0회(재시도 대기 1.5초로 테스트가 느려지지 않게). */
    private ClaudeLlmClient client(String fallback, Duration limit) {
        ClaudeProperties props = new ClaudeProperties("test-key", "m1", fallback, baseUrl(),
                limit, limit, 0, null, null, false, 0);
        return new ClaudeLlmClient(props, JSON);
    }

    private ClaudeLlmClient client() {
        return client(null, Duration.ofSeconds(5));
    }

    private void reply(String text) {
        reply(text, "end_turn");
    }

    private void reply(String text, String stopReason) {
        replies.add(new Reply(200, message(text, stopReason), 0));
    }

    private void replyError(int status, String type) {
        replies.add(new Reply(status, "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"x\"}}", 0));
    }

    /** 사고 블록(빈 텍스트, 기본 display=omitted) 다음에 텍스트 블록이 오는 응답. */
    private static String message(String text, String stopReason) {
        return JSON.writeValueAsString(Map.of(
                "id", "msg_1", "type", "message", "role", "assistant", "model", "m1",
                "content", List.of(
                        Map.of("type", "thinking", "thinking", "", "signature", "sig"),
                        Map.of("type", "text", "text", text)),
                "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 1, "output_tokens", 1)));
    }

    private static void assertError(ErrorCode expected, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // --- 요청 모양 ---------------------------------------------------------------------------

    @Test
    @DisplayName("구조화 출력·effort 로 요청하고 temperature 는 보내지 않는다 — 사고 블록은 건너뛰고 텍스트만 JSON 으로 읽는다")
    void requestShape() {
        reply("{\"ok\":true}");

        JsonNode result = client().generateJson("시스템", "안녕", SCHEMA);

        assertThat(result.path("ok").asBoolean()).isTrue();
        JsonNode req = requests.get(0);
        assertThat(credentials.get(0)).isIn("test-key", "Bearer test-key");
        assertThat(req.path("model").asString()).isEqualTo("m1");
        assertThat(req.path("max_tokens").asInt()).isEqualTo(4096);
        assertThat(req.path("system").asString()).isEqualTo("시스템");
        assertThat(req.has("temperature")).isFalse();
        assertThat(req.has("thinking")).isFalse();   // 모델 기본(adaptive)에 맡긴다
        assertThat(req.path("output_config").path("effort").asString()).isEqualTo("low");
        JsonNode format = req.path("output_config").path("format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("schema").path("type").asString()).isEqualTo("object");
        assertThat(format.path("schema").path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(req.path("messages").get(0).path("role").asString()).isEqualTo("user");
    }

    @Test
    @DisplayName("대화의 assistant 턴은 assistant 역할로 보낸다")
    void conversationRoles() {
        reply("{\"ok\":true}");

        client().generateJson("sys", List.of(
                LlmMessage.user("a"), LlmMessage.assistant("b"), LlmMessage.user("c")), SCHEMA);

        JsonNode messages = requests.get(0).path("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1).path("role").asString()).isEqualTo("assistant");
        assertThat(messages.get(2).path("role").asString()).isEqualTo("user");
    }

    @Test
    @DisplayName("이미지는 base64 이미지 블록으로 텍스트보다 앞에 — 비표준 image/jpg 는 image/jpeg 로")
    void imageBlock() {
        reply("{\"ok\":true}");

        client().generateJson("sys", "영수증", new byte[]{1, 2, 3}, "image/jpg", SCHEMA);

        JsonNode content = requests.get(0).path("messages").get(0).path("content");
        assertThat(content.get(0).path("type").asString()).isEqualTo("image");
        assertThat(content.get(0).path("source").path("type").asString()).isEqualTo("base64");
        assertThat(content.get(0).path("source").path("media_type").asString()).isEqualTo("image/jpeg");
        assertThat(content.get(0).path("source").path("data").asString()).isEqualTo("AQID");
        assertThat(content.get(1).path("text").asString()).isEqualTo("영수증");
    }

    // --- 실패 → 에러 코드 ----------------------------------------------------------------------

    @Test
    @DisplayName("키가 없으면 호출하지 않고 503 AI_UNAVAILABLE")
    void noKeyIsUnavailable() {
        ClaudeLlmClient noKey = new ClaudeLlmClient(
                new ClaudeProperties(null, null, null, baseUrl(), null, null, null, null, null, null, null), JSON);

        assertError(ErrorCode.AI_UNAVAILABLE, () -> noKey.generateJson("sys", "hi", SCHEMA));
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("거절(refusal)은 422 AI_PARSE_FAILED")
    void refusalIsParseFailed() {
        reply("", "refusal");

        assertError(ErrorCode.AI_PARSE_FAILED, () -> client().generateJson("sys", "hi", SCHEMA));
    }

    @Test
    @DisplayName("토큰 상한에서 잘린 응답은 422 AI_PARSE_FAILED")
    void maxTokensIsParseFailed() {
        reply("{\"ok\":", "max_tokens");

        assertError(ErrorCode.AI_PARSE_FAILED, () -> client().generateJson("sys", "hi", SCHEMA));
    }

    @Test
    @DisplayName("JSON 이 아닌 텍스트는 422 AI_PARSE_FAILED")
    void nonJsonIsParseFailed() {
        reply("죄송해요");

        assertError(ErrorCode.AI_PARSE_FAILED, () -> client().generateJson("sys", "hi", SCHEMA));
    }

    @Test
    @DisplayName("응답 본문을 읽을 수 없으면 500 이 아니라 503 AI_UNAVAILABLE")
    void unreadableBodyIsUnavailable() {
        replies.add(new Reply(200, "not-json-bytes", 0));

        assertError(ErrorCode.AI_UNAVAILABLE, () -> client().generateJson("sys", "hi", SCHEMA));
    }

    @Test
    @DisplayName("400·401 같은 설정 문제는 예비 모델로 넘어가지 않고 바로 503")
    void clientErrorDoesNotFallBack() {
        replyError(401, "authentication_error");

        assertError(ErrorCode.AI_UNAVAILABLE, () -> client("m2", Duration.ofSeconds(5)).generateJson("sys", "hi", SCHEMA));
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("429 를 받으면 재시도 없이 예비 모델로 넘어간다")
    void rateLimitFallsBack() {
        replyError(429, "rate_limit_error");
        reply("{\"ok\":true}");

        JsonNode result = client("m2", Duration.ofSeconds(5)).generateJson("sys", "hi", SCHEMA);

        assertThat(result.path("ok").asBoolean()).isTrue();
        assertThat(requests).extracting(r -> r.path("model").asString()).containsExactly("m1", "m2");
    }

    @Test
    @DisplayName("529(과부하)도 예비 모델로 넘어간다")
    void overloadedFallsBack() {
        replyError(529, "overloaded_error");
        reply("{\"ok\":true}");

        client("m2", Duration.ofSeconds(5)).generateJson("sys", "hi", SCHEMA);

        assertThat(requests).extracting(r -> r.path("model").asString()).containsExactly("m1", "m2");
    }

    @Test
    @DisplayName("상한을 넘기면 남은 재시도와 예비 모델을 건너뛰고 바로 503")
    void deadlineStopsRetriesAndFallback() {
        replies.add(new Reply(200, message("{\"ok\":true}", "end_turn"), 1_000));
        ClaudeLlmClient limited = client("m2", Duration.ofMillis(300));   // 생성(OkHttp 초기화)은 재지 않는다

        long started = System.nanoTime();
        assertError(ErrorCode.AI_UNAVAILABLE, () -> limited.generateJson("sys", "hi", SCHEMA));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(requests).hasSize(1);   // 예비 모델을 부르지 않았다
        assertThat(elapsedMillis).isLessThan(900);
    }

    // --- 설정 -------------------------------------------------------------------------------

    @Test
    @DisplayName("기본값: Haiku 5.5, effort low, 텍스트 10초·이미지 30초, 예비 모델 없음")
    void defaults() {
        ClaudeProperties props = new ClaudeProperties(null, null, "", null, null, null, null, null, null, null, null);

        assertThat(props.model()).isEqualTo("claude-haiku-5-5");
        assertThat(props.effort()).isEqualTo("low");
        assertThat(props.fallbackModel()).isNull();
        assertThat(props.timeout()).hasSeconds(10);
        assertThat(props.imageTimeout()).hasSeconds(30);
    }

    @Test
    @DisplayName("잘못된 effort 는 기동 시점에 실패한다 — 뜨고 나서 모든 호출이 503 이 되지 않게")
    void invalidEffortFailsFast() {
        assertThatThrownBy(() -> new ClaudeProperties(null, null, null, null, null, null, null, null, "fast", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claude.effort");
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
