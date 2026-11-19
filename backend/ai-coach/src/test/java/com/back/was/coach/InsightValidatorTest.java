package com.back.was.coach;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InsightValidatorTest {

    private final DemoData demo = TestSupport.demo();
    private final InsightValidator validator = new InsightValidator(new NumberCheck(TestSupport.OM));

    private InsightReport saved(String persona) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/demo/insights/" + persona + ".json")) {
            JsonNode root = TestSupport.OM.readTree(in);
            return TestSupport.OM.treeToValue(root.get("report"), InsightReport.class);
        }
    }

    /** Python 이 검증을 통과시켜 저장한 인사이트 3개는 Java 검증기도 통과해야 한다 */
    @Test
    void savedPythonInsightsPass() throws Exception {
        Aggregator agg = new Aggregator(demo);
        for (var p : demo.all()) {
            List<String> problems = validator.validate(saved(p.id()), agg.summarize(p.id()));
            assertTrue(problems.isEmpty(), p.id() + " -> " + problems);
        }
    }

    @Test
    void rejectsInventedNumber() throws Exception {
        Aggregator agg = new Aggregator(demo);
        InsightReport r = saved("college_delivery");
        var first = r.insights().get(0);
        var bad = new InsightReport.Insight(first.title(), first.category(), first.severity(),
                first.message() + " 연간 987,654원이 더 들어요.", first.evidence(), first.action());
        var list = new java.util.ArrayList<>(r.insights());
        list.set(0, bad);
        List<String> problems = validator.validate(new InsightReport(r.headline(), list), agg.summarize("college_delivery"));
        assertTrue(problems.stream().anyMatch(s -> s.contains("987,654")), problems.toString());
    }

    @Test
    void rejectsTooFewInsights() {
        var problems = validator.validate(new InsightReport("h", List.of()), new Aggregator(demo).summarize("frugal_saver"));
        assertTrue(problems.stream().anyMatch(s -> s.contains("3~4개")));
    }
}
