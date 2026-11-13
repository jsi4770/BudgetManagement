package com.back.was.coach;

import com.back.was.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코치 데모 API. 경로·응답 모양은 원본 FastAPI 서버와 같아서 static/index.html 이 그대로 동작한다.
 * 오류는 { "detail": "..." } 로 돌려준다 (UI 가 이 필드를 읽음).
 */
@RestController
@RequestMapping("/api")
public class CoachController {

    private final DemoData data;
    private final Aggregator aggregator;
    private final InsightService insights;
    private final GoalService goals;
    private final CoachChatService chat;
    private final CoachStore store;
    private final ObjectMapper om;

    public CoachController(DemoData data, Aggregator aggregator, InsightService insights, GoalService goals,
                           CoachChatService chat, CoachStore store, ObjectMapper om) {
        this.data = data;
        this.aggregator = aggregator;
        this.insights = insights;
        this.goals = goals;
        this.chat = chat;
        this.store = store;
        this.om = om;
    }

    @GetMapping("/personas")
    public List<Map<String, String>> personas() {
        return data.all().stream().map(p -> Map.of("id", p.id(), "name", p.name())).toList();
    }

    @GetMapping("/summary/{persona}")
    public Summary summary(@PathVariable String persona) {
        check(persona);
        return aggregator.summarize(persona);
    }

    @GetMapping("/insights/{persona}")
    public Map<String, Object> insights(@PathVariable String persona, @RequestParam(defaultValue = "false") boolean refresh) {
        check(persona);
        JsonNode saved = store.insight(persona);
        Map<String, Object> out = new LinkedHashMap<>();
        if (saved != null && !refresh) {
            out.put("report", saved.path("report"));
            out.put("problems", saved.path("problems"));
            out.put("cached", true);
            return out;
        }
        InsightService.Result r = insights.generateAndSave(persona);
        out.put("report", r.report());
        out.put("problems", r.problems());
        out.put("cached", false);
        return out;
    }

    @GetMapping("/goal/{persona}")
    public Object goal(@PathVariable String persona) {
        check(persona);
        JsonNode g = goals.loadWithProgress(persona);
        return g != null ? g : Map.of("goal", Map.of());
    }

    public record GoalReq(String text) {}

    @PostMapping("/goal/{persona}")
    public Map<String, Object> postGoal(@PathVariable String persona, @RequestBody GoalReq req) {
        check(persona);
        Map<String, Object> r = goals.createGoal(persona, req.text().strip());
        if (!Boolean.TRUE.equals(r.get("ok"))) return Map.of("ok", false, "ask", r.get("ask"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.putAll(om.convertValue(goals.loadWithProgress(persona), new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        return out;
    }

    public record ChatReq(String message) {}

    @PostMapping("/chat/{persona}")
    public Map<String, Object> chat(@PathVariable String persona, @RequestBody ChatReq req) {
        check(persona);
        CoachChatService.Answer a = chat.ask(persona, req.message().strip());
        return Map.of("answer", a.answer(), "verified", a.problems().isEmpty());
    }

    @PostMapping("/chat/{persona}/reset")
    public Map<String, Object> reset(@PathVariable String persona) {
        check(persona);
        chat.reset(persona);
        return Map.of("ok", true);
    }

    private void check(String persona) {
        if (!data.exists(persona)) throw new IllegalArgumentException("알 수 없는 페르소나");
    }

    // ---- 오류 응답 ({"detail": ...}) ----
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> notFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler({BusinessException.class, RuntimeException.class})
    public ResponseEntity<Map<String, String>> aiFailure(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail",
                "AI 응답을 가져오지 못했어요. 잠시 후 다시 시도해 주세요. (" + e.getClass().getSimpleName() + ")"));
    }
}
