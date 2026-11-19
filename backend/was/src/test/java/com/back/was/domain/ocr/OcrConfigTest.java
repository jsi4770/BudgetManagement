package com.back.was.domain.ocr;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OcrConfig} 가 만든 실제 HTTP 클라이언트로 요청 헤더를 확인한다.
 * {@link DjangoReceiptOcrClientTest} 는 MockRestServiceServer 라 요청 팩토리를 거치지 않아서 이걸 못 본다.
 */
class OcrConfigTest {

    private HttpServer server;
    private final AtomicReference<String> contentLength = new AtomicReference<>();
    private final AtomicReference<String> transferEncoding = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/receipts/ocr", exchange -> {
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = """
                    {"success":true,"data":{"amount":1000,"transactionDate":null,"category":null,"memo":null}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("multipart 를 Content-Length 로 보낸다 — Django runserver 는 chunked 본문을 400 으로 거절한다")
    void sendsContentLengthNotChunked() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var client = new DjangoReceiptOcrClient(
                new OcrConfig().ocrRestClient(new OcrProperties(baseUrl, Duration.ofSeconds(5))),
                new ObjectMapper());

        client.analyze(new ReceiptImage(new byte[64 * 1024], "image/jpeg"), CATEGORIES);

        assertThat(transferEncoding.get()).isNull();
        assertThat(contentLength.get()).isNotNull();
        assertThat(Long.parseLong(contentLength.get())).isGreaterThan(64 * 1024);
    }
}
