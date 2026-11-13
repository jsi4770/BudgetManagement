package com.back.was.eval;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.coach.*;
import com.back.was.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 인사이트 생성(첫 시도 통과율·재시도 횟수), 목표 의도 파싱 정확도, 코치 대화의 숫자 검증 통과율을 실제 Gemini 로 잰다 */
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class CoachLiveEval extends LiveEvalBase {

    @Autowired GeminiClient gemini;
    @Autowired Aggregator aggregator;
    @Autowired InsightValidator validator;
    @Autowired CoachStore store;
    @Autowired ObjectMapper om;
    @Autowired GoalService goals;
    @Autowired CoachChatService chat;
    @Autowired DemoData demo;

    @Test
    void insights() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GeminiClient counting = new GeminiClient() {
            public JsonNode generateJson(String s, List<com.back.was.ai.gemini.GeminiMessage> m, java.util.Map<String, Object> sc, double t) {
                calls.incrementAndGet();
                return gemini.generateJson(s, m, sc, t);
            }
            public JsonNode generateJson(String s, String u, byte[] i, String mt, java.util.Map<String, Object> sc, double t) {
                calls.incrementAndGet();
                return gemini.generateJson(s, u, i, mt, sc, t);
            }
        };
        InsightService svc = new InsightService(counting, aggregator, validator, store, om);
        List<String> rows = new ArrayList<>();
        int runs = 0, firstTry = 0, finalPass = 0, unavailable = 0;
        List<Long> lat = new ArrayList<>();
        for (int r = 0; r < n(); r++) {
            for (var p : demo.all()) {
                calls.set(0);
                long t0 = System.nanoTime();
                try {
                    var res = svc.generate(aggregator.summarize(p.id()));
                    lat.add(System.nanoTime() - t0);
                    runs++;
                    if (calls.get() == 1) firstTry++;
                    if (res.problems().isEmpty()) finalPass++;
                    rows.add(String.format("| %s | %d회 | %s | %.1fs |", p.id(), calls.get(),
                            res.problems().isEmpty() ? "통과" : "미통과 " + res.problems().size() + "건", (System.nanoTime() - t0) / 1e9));
                } catch (BusinessException e) { unavailable++; }
            }
        }
        long[] nanos = lat.stream().mapToLong(Long::longValue).toArray();
        EvalReport.write("insight-quality.md", "# 인사이트 생성 품질 (실제 Gemini)\n\n"
                + String.format("- 첫 시도 검증 통과: **%d/%d**\n- 재시도 후 최종 통과: **%d/%d**\n- AI 호출 실패(제외): %d\n- 지연: p50 %.1fs, p95 %.1fs\n\n",
                firstTry, runs, finalPass, runs, unavailable, EvalReport.pct(nanos, 0.5) / 1000, EvalReport.pct(nanos, 0.95) / 1000)
                + "| 페르소나 | LLM 호출 | 결과 | 소요 |\n|---|---|---|---|\n" + String.join("\n", rows) + "\n");
    }

    record IntentCase(String text, String type, Long amount, Integer months, String category, Long limit, boolean clarify) {}

    @Test
    void goalIntent() throws Exception {
        List<String> cats = aggregator.summarize("college_delivery").categories().stream().map(Summary.CategoryStat::category).toList();
        List<IntentCase> cases = List.of(
                new IntentCase("6개월 안에 100만원 모으고 싶어", "save_amount", 1_000_000L, 6, null, null, false),
                new IntentCase("1년 안에 1200만원 모을래", "save_amount", 12_000_000L, 12, null, null, false),
                new IntentCase("300만원 모으기 6개월", "save_amount", 3_000_000L, 6, null, null, false),
                new IntentCase("2년동안 2400만원", "save_amount", 24_000_000L, 24, null, null, false),
                new IntentCase("배달비를 월 20만원 이하로", "cap_category", null, null, "배달", 200_000L, false),
                new IntentCase("카페는 한 달에 3만원만 쓸래", "cap_category", null, null, "카페", 30_000L, false),
                new IntentCase("쇼핑비 월 5만원 아래로 줄이고 싶어", "cap_category", null, null, "쇼핑", 50_000L, false),
                new IntentCase("돈 좀 모으고 싶어", null, null, null, null, null, true));
        int ok = 0, total = 0, unavailable = 0;
        List<String> misses = new ArrayList<>();
        for (IntentCase c : cases) {
            try {
                var i = goals.parseIntent(c.text(), "2026-10", cats);
                total++;
                boolean good = c.clarify()
                        ? (i.needsClarification() != null && !i.needsClarification().isBlank())
                        : c.type().equals(i.goalType())
                        && (c.amount() == null || c.amount().equals(i.targetAmount()))
                        && (c.months() == null || c.months().equals(i.months()))
                        && (c.category() == null || c.category().equals(i.category()))
                        && (c.limit() == null || c.limit().equals(i.monthlyLimit()));
                if (good) ok++; else misses.add("`" + c.text() + "` → " + i);
            } catch (BusinessException e) { unavailable++; }
        }
        EvalReport.write("goal-intent.md", String.format("# 목표 문장 해석 정확도 (실제 Gemini)\n\n- 정확: **%d/%d**\n- AI 호출 실패(제외): %d\n\n%s",
                ok, total, unavailable, misses.isEmpty() ? "틀린 케이스 없음\n" : "## 틀린 케이스\n" + misses.stream().map(s -> "- " + s + "\n").reduce("", String::concat)));
    }

    @Test
    void coachChat() throws Exception {
        List<String> qs = List.of("이번 달 왜 이렇게 많이 쓴 것 같아?", "배달을 반으로 줄이면?", "내 목표 어떻게 돼가?", "비트코인 사도 돼?");
        int total = 0, verified = 0, unavailable = 0;
        List<Long> lat = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < n(); r++) {
            chat.reset("college_delivery");
            for (String q : qs) {
                long t0 = System.nanoTime();
                try {
                    var a = chat.ask("college_delivery", q);
                    lat.add(System.nanoTime() - t0);
                    total++;
                    if (a.problems().isEmpty()) verified++;
                    rows.add("| " + q + " | " + (a.problems().isEmpty() ? "통과" : "미통과") + " | " + a.answer().replace("\n", " ").substring(0, Math.min(60, a.answer().length())) + "… |");
                } catch (BusinessException e) { unavailable++; }
            }
        }
        long[] nanos = lat.stream().mapToLong(Long::longValue).toArray();
        EvalReport.write("coach-chat.md", String.format("# 코치 대화 숫자 검증 (실제 Gemini)\n\n- 숫자 검증 통과: **%d/%d**\n- AI 호출 실패(제외): %d\n- 턴 지연: p50 %.1fs, p95 %.1fs\n\n| 질문 | 검증 | 답변 앞부분 |\n|---|---|---|\n%s\n",
                verified, total, unavailable, EvalReport.pct(nanos, 0.5) / 1000, EvalReport.pct(nanos, 0.95) / 1000, String.join("\n", rows)));
    }
}
