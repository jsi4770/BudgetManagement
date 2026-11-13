package com.back.was.coach;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** 서버를 실제로 띄워(Gemini mock) 주요 경로가 응답하는지 확인 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gemini.mock=true")
class CoachApiSmokeTest {

    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void personasAndSummary() throws Exception {
        assertTrue(get("/api/personas").body().contains("college_delivery"));
        var r = get("/api/summary/college_delivery");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"savings_rate\""));
    }

    @Test void unknownPersonaIs404() throws Exception {
        var r = get("/api/summary/nobody");
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("detail"));
    }

    @Test void savedInsightIsServedFromCache() throws Exception {
        var r = get("/api/insights/college_delivery");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"cached\":true"));
    }

    @Test void chatWorksWithMock() throws Exception {
        var r = post("/api/chat/frugal_saver", "{\"message\":\"안녕\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("answer"));
    }

    @Test void indexPageIsServed() throws Exception {
        assertTrue(get("/").body().contains("<html"));
    }

    @Test void seedThenWeeklyAnalysis() throws Exception {
        assertEquals(200, post("/dev/seed", "{}").statusCode());
        var r = get("/analysis/categories?period=WEEK");
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("isAnomaly"));
    }
}
