package com.back.was.perf;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** 간단한 부하 생성기: 동시 접속 conc 개가 총 n 건을 소화하고 지연 분포·처리량·오류 수를 잰다 */
final class LoadGen {

    record Stat(String name, int conc, int n, double rps, double p50, double p95, double p99, double max, int errors) {
        String row() {
            return String.format("| %s | %d | %d | %.0f | %.2f | %.2f | %.2f | %.2f | %d |",
                    name, conc, n, rps, p50, p95, p99, max, errors);
        }
    }

    static final String HEADER = "| 시나리오 | 동시접속 | 요청수 | 처리량(req/s) | p50(ms) | p95(ms) | p99(ms) | 최대(ms) | 오류 |\n"
            + "|---|---|---|---|---|---|---|---|---|";

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    static HttpRequest get(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
    }

    static HttpRequest post(int port, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
    }

    static Stat run(String name, int conc, int n, Supplier<HttpRequest> req) throws Exception {
        long[] nanos = new long[n];
        AtomicInteger next = new AtomicInteger(), errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(conc);
        long t0 = System.nanoTime();
        for (int c = 0; c < conc; c++) {
            pool.submit(() -> {
                int i;
                while ((i = next.getAndIncrement()) < n) {
                    long s = System.nanoTime();
                    try {
                        HttpResponse<Void> r = HTTP.send(req.get(), HttpResponse.BodyHandlers.discarding());
                        if (r.statusCode() / 100 != 2) errors.incrementAndGet();
                    } catch (IOException | InterruptedException e) {
                        errors.incrementAndGet();
                    }
                    nanos[i] = System.nanoTime() - s;
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.MINUTES);
        double secs = (System.nanoTime() - t0) / 1e9;
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return new Stat(name, conc, n, n / secs, ms(sorted, 0.50), ms(sorted, 0.95), ms(sorted, 0.99),
                sorted[n - 1] / 1e6, errors.get());
    }

    private static double ms(long[] sorted, double q) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(q * sorted.length) - 1)] / 1e6;
    }

    static void warmup(int n, Supplier<HttpRequest> req) throws Exception {
        run("warmup", 4, n, req);
    }

    static void writeReport(String file, String markdown) throws IOException {
        Path dir = Path.of(System.getProperty("report.dir", "build/perf-report"));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), markdown);
        System.out.println("\n" + markdown + "\n(저장: " + dir.resolve(file).toAbsolutePath() + ")");
    }

    static List<String> lines() { return new ArrayList<>(); }
}
