package com.back.was.perf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;

/**
 * 실제 AI 처럼 느린 응답(mock 지연)일 때 동시 사용자를 얼마나 감당하는가.
 * (대화 API 는 한 사용자의 대화 순서를 지키려고 세션 단위로 직렬화하므로 무상태인 파싱 API 로 잰다)
 * 이론상 처리량 = 동시접속 / 지연. 서버가 이보다 눈에 띄게 낮으면 서버 쪽 병목이다.
 */
abstract class LlmLatencyBase {

    static final int DELAY_MS = 400;
    @LocalServerPort int port;

    abstract String label();

    @Test
    void measure() throws Exception {
        var chat = (java.util.function.Supplier<java.net.http.HttpRequest>)
                () -> LoadGen.post(port, "/transactions/parse", "{\"text\":\"배민 3.4\"}");
        LoadGen.warmup(40, chat);
        List<String> rows = new ArrayList<>();
        for (int conc : new int[]{1, 8, 32, 128, 256}) {
            var st = LoadGen.run("POST /transactions/parse (AI " + DELAY_MS + "ms)", conc, Math.max(60, conc * 4), chat);
            rows.add(st.row() + String.format(" %.0f |", conc * 1000.0 / DELAY_MS));
        }
        String header = "| 시나리오 | 동시접속 | 요청수 | 처리량(req/s) | p50(ms) | p95(ms) | p99(ms) | 최대(ms) | 오류 | 이론치(req/s) |\n"
                + "|---|---|---|---|---|---|---|---|---|---|";
        String md = "# AI 지연 " + DELAY_MS + "ms 가정 — " + label() + "\n\n" + header + "\n" + String.join("\n", rows) + "\n";
        LoadGen.writeReport("llm-latency-" + label().replace(' ', '-') + ".md", md);
    }
}
