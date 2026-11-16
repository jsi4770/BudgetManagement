package com.back.was.domain.ocr;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ocrRestClient} 가 실제로 내보내는 요청 헤더. {@code MockRestServiceServer} 는 요청 팩토리를 건너뛰므로
 * 이 차이는 거기서 보이지 않는다.
 */
class OcrRestClientTest {

    private HttpServer server;
    private final AtomicReference<String> contentLength = new AtomicReference<>();
    private final AtomicReference<String> transferEncoding = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/receipts/ocr", exchange -> {
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("multipart 본문을 chunked 가 아니라 Content-Length 로 보낸다 — Django runserver 는 chunked 본문을 못 읽는다")
    void sendsContentLengthNotChunked() {
        OcrProperties props = new OcrProperties("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("categories", "[]");

        new OcrConfig().ocrRestClient(props).post()
                .uri("/receipts/ocr")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toBodilessEntity();

        assertThat(transferEncoding.get()).isNull();
        assertThat(contentLength.get()).isNotNull();
    }
}
