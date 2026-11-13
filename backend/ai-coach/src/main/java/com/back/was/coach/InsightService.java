package com.back.was.coach;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.ai.gemini.GeminiSchema;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 요약 → 인사이트 카드(구조화 출력) + 숫자 검증 + 재시도 */
@Service
public class InsightService {

    public record Result(Summary summary, InsightReport report, List<String> problems) {}

    private static final int MAX_RETRY = 3;
    private static final String SYSTEM = Prompts.load("insight-system.txt");
    private static final Map<String, Object> SCHEMA = schema();

    private final GeminiClient gemini;
    private final Aggregator aggregator;
    private final InsightValidator validator;
    private final CoachStore store;
    private final ObjectMapper om;

    public InsightService(GeminiClient gemini, Aggregator aggregator, InsightValidator validator,
                          CoachStore store, ObjectMapper om) {
        this.gemini = gemini;
        this.aggregator = aggregator;
        this.validator = validator;
        this.store = store;
        this.om = om;
    }

    /** 새로 생성하고 저장 */
    public Result generateAndSave(String persona) {
        Summary summary = aggregator.summarize(persona);
        Map<String, Object> saved = new LinkedHashMap<>();
        Result r = generate(summary);
        saved.put("summary", r.summary());
        saved.put("report", r.report());
        saved.put("problems", r.problems());
        store.putInsight(persona, om.valueToTree(saved));
        return r;
    }

    public Result generate(Summary summary) {
        String goalNote = store.hasGoal(summary.persona()) ? "사용자 목표: 있음" : "사용자 목표: 없음";
        String prompt = summary.name() + " 님의 " + summary.month() + " 월간 소비 요약이다. (" + goalNote + ")\n\n"
                + om.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        prompt = "다음은 " + prompt;

        List<String> problems = List.of();
        InsightReport report = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            String p = prompt;
            if (!problems.isEmpty()) {
                p += "\n\n이전 응답에서 아래 문제가 있었다. 입력 JSON의 값만 사용해 다시 작성해라:\n- " + String.join("\n- ", problems);
            }
            JsonNode node = gemini.generateJson(SYSTEM, p, SCHEMA, 0.4);
            report = om.treeToValue(node, InsightReport.class);
            problems = validator.validate(report, summary);
            if (problems.isEmpty()) return new Result(summary, report, List.of());
        }
        return new Result(summary, report, problems);   // 끝내 실패하면 마지막 결과와 문제 목록을 같이 반환
    }

    private static Map<String, Object> schema() {
        Map<String, Map<String, Object>> ev = new LinkedHashMap<>();
        ev.put("label", GeminiSchema.string("사용자에게 보이는 한국어 라벨"));
        ev.put("value", GeminiSchema.number("입력 JSON 의 값 그대로"));
        ev.put("unit", enumString("원", "%", "건", "개", "개월"));
        Map<String, Map<String, Object>> in = new LinkedHashMap<>();
        in.put("title", GeminiSchema.string(null));
        in.put("category", GeminiSchema.string(null));
        in.put("severity", enumString("info", "warn", "alert"));
        in.put("message", GeminiSchema.string("사용자에게 보일 설명 (2~3문장)"));
        in.put("evidence", GeminiSchema.array(GeminiSchema.object(ev), null));
        in.put("action", GeminiSchema.string("바로 해볼 수 있는 구체적 제안 1개"));
        Map<String, Map<String, Object>> root = new LinkedHashMap<>();
        root.put("headline", GeminiSchema.string(null));
        root.put("insights", GeminiSchema.array(GeminiSchema.object(in), null));
        return GeminiSchema.object(root);
    }

    static Map<String, Object> enumString(String... values) {
        Map<String, Object> m = GeminiSchema.string(null);
        m.put("enum", List.of(values));
        return m;
    }
}
