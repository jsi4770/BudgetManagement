package com.back.was.coach;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.ai.gemini.GeminiMessage;
import com.back.was.ai.gemini.GeminiSchema;
import com.back.was.ai.gemini.GeminiTool;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 코칭 대화. LLM 은 숫자를 직접 계산하지 않고 도구(집계 함수)를 호출해 얻는다. 답변 숫자는 도구 결과와 대조해 검증한다. */
@Service
public class CoachChatService {

    private static final String SYSTEM = Prompts.load("chat-system.txt");

    private final GeminiClient gemini;
    private final Aggregator aggregator;
    private final DemoData data;
    private final GoalService goals;
    private final CoachStore store;
    private final NumberCheck numbers;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public CoachChatService(GeminiClient gemini, Aggregator aggregator, DemoData data, GoalService goals,
                            CoachStore store, NumberCheck numbers) {
        this.gemini = gemini;
        this.aggregator = aggregator;
        this.data = data;
        this.goals = goals;
        this.store = store;
        this.numbers = numbers;
    }

    public record Answer(String answer, List<String> problems) {}

    public Answer ask(String persona, String text) {
        return sessions.computeIfAbsent(persona, this::newSession).ask(text);
    }

    public void reset(String persona) { sessions.remove(persona); }

    private Session newSession(String persona) {
        data.get(persona);   // 존재 확인
        return new Session(persona);
    }

    /** 페르소나별 대화 상태. 한 사용자가 동시에 두 번 보내도 순서가 섞이지 않게 ask 는 직렬화한다. */
    final class Session {
        private final String persona;
        private final List<Object> seen = new ArrayList<>();      // 도구가 돌려준 값 (답변 숫자 검증용)
        private final List<GeminiMessage> history = new ArrayList<>();
        private final List<String> userTexts = new ArrayList<>();
        private final List<GeminiTool> tools;
        private final String system;

        Session(String persona) {
            this.persona = persona;
            this.tools = buildTools();
            Summary s = aggregator.summarize(persona);
            this.system = SYSTEM.replace("{name}", s.name()).replace("{month}", s.month());
        }

        synchronized Answer ask(String text) {
            userTexts.add(text);
            history.add(GeminiMessage.user(text));
            String answer = gemini.chatWithTools(system, history, tools, 0.3);
            List<String> problems = check(answer);
            if (!problems.isEmpty()) {
                String fix = "방금 답변의 다음 숫자는 도구 결과에 없는 값이야:\n- " + String.join("\n- ", problems)
                        + "\n도구가 준 값만 사용해서 다시 답해줘. 필요하면 도구를 다시 호출해.";
                history.add(GeminiMessage.model(answer));
                history.add(GeminiMessage.user(fix));
                answer = gemini.chatWithTools(system, history, tools, 0.3);
                problems = check(answer);
            }
            history.add(GeminiMessage.model(answer));
            return new Answer(answer, problems);
        }

        private List<String> check(String answer) {
            Set<Double> allowed = numbers.allowed(seen);
            // 사용자가 직접 말한 숫자("30% 줄이면")는 되풀이해도 괜찮다
            for (String u : userTexts) {
                var m = java.util.regex.Pattern.compile("\\d[\\d,]*\\.?\\d*").matcher(NumberCheck.normalize(u));
                while (m.find()) allowed.add(Double.parseDouble(m.group().replace(",", "").replaceAll("\\.$", "")));
            }
            return numbers.unknownNumbers(List.of(answer), allowed);
        }

        private GeminiTool tool(String name, String desc, Map<String, Object> params,
                                java.util.function.Function<JsonNode, Object> fn) {
            return new GeminiTool(name, desc, params, args -> {
                Object out = fn.apply(args);
                synchronized (seen) { seen.add(out); }
                return out;
            });
        }

        private List<GeminiTool> buildTools() {
            List<Tx> raw = data.get(persona).txs();
            List<String> months = raw.stream().map(Tx::month).distinct().sorted().toList();
            String latest = months.get(months.size() - 1);

            Map<String, Map<String, Object>> monthOnly = new LinkedHashMap<>();
            monthOnly.put("month", GeminiSchema.string("'YYYY-MM'. 비우면 가장 최근 달"));
            Map<String, Map<String, Object>> catOnly = new LinkedHashMap<>();
            catOnly.put("category", GeminiSchema.string("예: '배달', '카페'"));
            Map<String, Map<String, Object>> list = new LinkedHashMap<>();
            list.put("category", GeminiSchema.string("비우면 전체"));
            list.put("month", GeminiSchema.string("'YYYY-MM'. 비우면 전체"));
            list.put("limit", GeminiSchema.integer("가져올 개수 (기본 8, 최대 20)"));
            Map<String, Map<String, Object>> sim = new LinkedHashMap<>();
            sim.put("category", GeminiSchema.string(null));
            sim.put("cut_pct", GeminiSchema.integer("줄일 비율(%)"));

            return List.of(
                tool("get_month_summary", "한 달의 소비 요약(소득, 지출, 저축률, 카테고리별 금액·전월 대비·추세, 월말 집중, 이상 징후)을 가져온다.",
                    GeminiSchema.object(monthOnly, List.of()), args -> {
                        String month = args.path("month").asString("");
                        if (!month.isEmpty() && !months.contains(month)) {
                            return Map.of("error", "데이터가 있는 달: " + months.get(0) + " ~ " + latest);
                        }
                        return aggregator.summarize(persona, month.isEmpty() ? null : month);
                    }),
                tool("get_category_history", "특정 카테고리의 월별 지출 금액과 건수 추이를 가져온다. 예: '배달', '카페'.",
                    GeminiSchema.object(catOnly), args -> {
                        String cat = args.path("category").asString("");
                        Map<String, long[]> byM = new TreeMap<>();
                        for (Tx t : raw) {
                            if (t.isExpense() && t.category().equals(cat)) {
                                long[] v = byM.computeIfAbsent(t.month(), k -> new long[2]);
                                v[0] += t.amount();
                                v[1]++;
                            }
                        }
                        if (byM.isEmpty()) {
                            return Map.of("error", "없는 카테고리. 가능한 값: "
                                    + raw.stream().filter(Tx::isExpense).map(Tx::category).distinct().sorted().toList());
                        }
                        Map<String, Object> monthly = new LinkedHashMap<>();
                        byM.forEach((m, v) -> monthly.put(m, Map.of("amount", v[0], "count", v[1])));
                        return Map.of("category", cat, "monthly", monthly);
                    }),
                tool("list_transactions", "거래 내역을 금액 큰 순으로 가져온다. category, month('YYYY-MM')로 필터. 가장 비싼 지출을 물을 때 쓴다.",
                    GeminiSchema.object(list, List.of()), args -> {
                        String cat = args.path("category").asString(""), month = args.path("month").asString("");
                        int limit = Math.max(1, Math.min(args.path("limit").asInt(8), 20));
                        List<Tx> rows = raw.stream().filter(t -> t.isExpense()
                                && (cat.isEmpty() || t.category().equals(cat))
                                && (month.isEmpty() || t.month().equals(month)))
                                .sorted(Comparator.comparingLong(Tx::amount).reversed()).toList();
                        List<Map<String, Object>> out = new ArrayList<>();
                        for (Tx t : rows.subList(0, Math.min(limit, rows.size()))) {
                            out.add(new LinkedHashMap<>(Map.of("date", t.date(), "category", t.category(),
                                    "merchant", t.merchant(), "amount", t.amount())));
                        }
                        return Map.of("count", rows.size(), "transactions", out);
                    }),
                tool("simulate_cut", "카테고리 지출을 cut_pct(%) 줄이면 월·연 절약액과 새 저축률이 어떻게 되는지 계산한다. '반으로 줄이면?' 같은 질문에 쓴다.",
                    GeminiSchema.object(sim), args -> {
                        String cat = args.path("category").asString("");
                        Summary s = aggregator.summarize(persona);
                        Summary.CategoryStat c = s.categories().stream().filter(x -> x.category().equals(cat)).findFirst().orElse(null);
                        if (c == null) return Map.of("error", "이번 달 지출이 없는 카테고리: " + cat);
                        int pct = Math.max(0, Math.min(100, args.path("cut_pct").asInt(0)));
                        long saved = GoalService.r(c.amount() * pct / 100.0);
                        double after = Aggregator.round((s.income() - (s.spending() - saved)) * 1.0 / s.income() * 100, 1);
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("category", cat); out.put("current_month_amount", c.amount()); out.put("cut_pct", pct);
                        out.put("monthly_saving", saved); out.put("yearly_saving", saved * 12);
                        out.put("savings_rate_now", s.savingsRate()); out.put("savings_rate_after", after);
                        return out;
                    }),
                tool("get_goal_progress", "사용자가 세운 목표와 현재 진행 상황, 월별 계획을 가져온다. 목표가 없으면 그렇다고 알려준다.",
                    null, args -> {
                        JsonNode saved = store.goal(persona);
                        if (saved == null) return Map.of("has_goal", false);
                        JsonNode g = saved.path("goal");
                        Map<String, Object> plan = new LinkedHashMap<>();
                        g.path("plan").properties().forEach(e -> {
                            if (!e.getKey().equals("baseline") && !e.getKey().equals("schedule")) plan.put(e.getKey(), e.getValue());
                        });
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("has_goal", true);
                        for (String k : List.of("title", "type", "start_month", "target_amount", "months", "category", "monthly_limit", "status")) {
                            out.put(k, g.path(k));
                        }
                        out.put("plan", plan);
                        out.put("progress", goals.progress(g, persona));
                        out.put("note", "start_month 이전이면 아직 시작 전이다. months_elapsed가 0이면 실적이 없다.");
                        return out;
                    }));
        }
    }
}
