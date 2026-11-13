package com.back.was.perf;

import com.back.was.analysis.AnomalyJudge;
import com.back.was.coach.Aggregator;
import com.back.was.coach.InsightValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버 자체의 오버헤드 측정 (Gemini mock, 지연 0). AI 호출 시간을 뺀 "우리 코드"의 속도다.
 * 실행: ./gradlew perf
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"gemini.mock=true"})
class ThroughputPerfTest {

    @LocalServerPort int port;
    @Autowired Aggregator aggregator;

    @Test
    void measure() throws Exception {
        LoadGen.post(port, "/dev/seed", "{}");
        java.net.http.HttpClient.newHttpClient().send(LoadGen.post(port, "/dev/seed", "{}"),
                java.net.http.HttpResponse.BodyHandlers.discarding());

        record Scenario(String name, java.util.function.Supplier<java.net.http.HttpRequest> req) {}
        List<Scenario> scenarios = List.of(
                new Scenario("GET /api/summary (집계 계산)", () -> LoadGen.get(port, "/api/summary/college_delivery")),
                new Scenario("GET /api/insights (저장본)", () -> LoadGen.get(port, "/api/insights/college_delivery")),
                new Scenario("GET /analysis/categories?WEEK (이상치)", () -> LoadGen.get(port, "/analysis/categories?period=WEEK")),
                new Scenario("GET /analysis/monthly", () -> LoadGen.get(port, "/analysis/monthly")),
                new Scenario("POST /transactions/parse (mock AI)", () -> LoadGen.post(port, "/transactions/parse", "{\"text\":\"배민 3.4\"}")),
                new Scenario("POST /api/chat (mock AI)", () -> LoadGen.post(port, "/api/chat/frugal_saver", "{\"message\":\"안녕\"}")));

        List<String> rows = new ArrayList<>();
        for (Scenario s : scenarios) {
            LoadGen.warmup(300, s.req());
            for (int conc : new int[]{1, 8, 32}) rows.add(LoadGen.run(s.name(), conc, 2000, s.req()).row());
        }

        StringBuilder md = new StringBuilder("# 서버 오버헤드 (Gemini mock, 지연 0)\n\n");
        md.append("- 환경: ").append(System.getProperty("os.name")).append(", JDK ").append(System.getProperty("java.version"))
                .append(", CPU ").append(Runtime.getRuntime().availableProcessors()).append("코어\n\n");
        md.append(LoadGen.HEADER).append('\n').append(String.join("\n", rows)).append("\n\n");
        md.append("## 순수 계산 마이크로 벤치 (HTTP 제외)\n\n").append(micro());
        LoadGen.writeReport("throughput.md", md.toString());
    }

    private String micro() {
        for (int i = 0; i < 3000; i++) aggregator.summarize("rookie_subscription");   // JIT 워밍업
        int n = 5000;
        long t = System.nanoTime();
        for (int i = 0; i < n; i++) aggregator.summarize("rookie_subscription");
        double aggUs = (System.nanoTime() - t) / 1e3 / n;

        List<Long> past = List.of(120_000L, 150_000L, 90_000L, 130_000L);
        long x = 0;
        t = System.nanoTime();
        for (int i = 0; i < 2_000_000; i++) x += AnomalyJudge.judge(300_000 + (i & 7), past).averageAmount();
        double judgeNs = (System.nanoTime() - t) / 2_000_000.0;
        return String.format("| 함수 | 평균 |\n|---|---|\n| Aggregator.summarize (7개월·약 700건) | %.0f µs/회 |\n| AnomalyJudge.judge | %.0f ns/회 |\n(%d)\n",
                aggUs, judgeNs, x % 2);
    }
}
