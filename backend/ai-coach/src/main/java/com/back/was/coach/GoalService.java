package com.back.was.coach;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.ai.gemini.GeminiSchema;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.*;

/**
 * 자연어 목표 → 구조화(LLM) → 현실성 판정·월별 계획(코드) → 설명(LLM, 숫자 검증).
 * 원본 Python goals.py 의 Java 이식. 평가 결과는 Python 과 같은 키의 Map 으로 다룬다(UI·프롬프트 호환).
 */
@Service
public class GoalService {

    static final List<String> DISCRETIONARY = List.of("배달", "외식", "카페", "쇼핑", "구독", "문화/여가");
    static final double MAX_CUT = 0.30;    // 카테고리당 최대 삭감 비율
    static final double COMFORT = 0.80;    // 필요액이 저축 여력의 80% 이하면 여유

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GoalIntent(String goalType, String title, Long targetAmount, Integer months,
                             String category, Long monthlyLimit, String needsClarification) {}

    public record GoalNarrative(String headline, String message, List<String> tips) {}

    private static final String INTENT_SYSTEM = Prompts.load("goal-intent-system.txt");
    private static final String NARR_SYSTEM = Prompts.load("goal-narrative-system.txt");

    private final GeminiClient gemini;
    private final Aggregator aggregator;
    private final DemoData data;
    private final NumberCheck numbers;
    private final CoachStore store;
    private final ObjectMapper om;

    public GoalService(GeminiClient gemini, Aggregator aggregator, DemoData data, NumberCheck numbers,
                       CoachStore store, ObjectMapper om) {
        this.gemini = gemini;
        this.aggregator = aggregator;
        this.data = data;
        this.numbers = numbers;
        this.store = store;
        this.om = om;
    }

    // ---------- 1) 의도 파싱 (LLM) ----------
    public GoalIntent parseIntent(String text, String todayMonth, List<String> categories) {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("goal_type", InsightService.enumString("save_amount", "cap_category"));
        p.put("title", GeminiSchema.string("목표 제목 (예: 배달비 줄이기)"));
        p.put("target_amount", GeminiSchema.nullable(GeminiSchema.integer("save_amount: 모으려는 총액(원)")));
        p.put("months", GeminiSchema.nullable(GeminiSchema.integer("기간(개월). 말하지 않았으면 null")));
        p.put("category", GeminiSchema.nullable(GeminiSchema.string("cap_category: 줄일 카테고리")));
        p.put("monthly_limit", GeminiSchema.nullable(GeminiSchema.integer("cap_category: 월 한도(원)")));
        p.put("needs_clarification", GeminiSchema.nullable(GeminiSchema.string("정보가 부족할 때 사용자에게 되물을 한 문장")));
        String prompt = "기준 월: " + todayMonth + "\n카테고리 목록: " + String.join(", ", categories) + "\n\n사용자 문장: " + text;
        JsonNode node = gemini.generateJson(INTENT_SYSTEM, prompt, GeminiSchema.object(p), 0.4);
        return om.treeToValue(node, GoalIntent.class);
    }

    // ---------- 2) 현실성 검증·계획 (코드) ----------
    static String addMonths(String ym, int n) {
        int t = Integer.parseInt(ym.substring(0, 4)) * 12 + (Integer.parseInt(ym.substring(5)) - 1) + n;
        return String.format("%d-%02d", t / 12, t % 12 + 1);
    }

    static long r(double x) { return (long) (Math.rint(x / 100) * 100); }

    /** 최근 n개월 평균: 소득, 지출, 저축 여력(소득-지출), 카테고리별 지출 */
    public Map<String, Object> baseline(String persona, int n) {
        List<Tx> txs = data.get(persona).txs();
        List<String> all = txs.stream().map(Tx::month).distinct().sorted().toList();
        List<String> months = all.subList(Math.max(0, all.size() - n), all.size());
        Map<String, Long> inc = new LinkedHashMap<>(), exp = new LinkedHashMap<>();
        months.forEach(m -> { inc.put(m, 0L); exp.put(m, 0L); });
        Map<String, Map<String, Long>> cat = new LinkedHashMap<>();
        for (Tx t : txs) {
            String m = t.month();
            if (!inc.containsKey(m)) continue;
            if (t.isIncome()) inc.merge(m, t.amount(), Long::sum);
            else if (!Aggregator.SAVING_CATS.contains(t.category())) {
                exp.merge(m, t.amount(), Long::sum);
                cat.computeIfAbsent(t.category(), k -> zeros(months)).merge(m, t.amount(), Long::sum);
            }
        }
        double surplus = months.stream().mapToLong(m -> inc.get(m) - exp.get(m)).average().orElse(0);
        Map<String, Object> catAvg = new LinkedHashMap<>();
        cat.forEach((c, v) -> catAvg.put(c, r(v.values().stream().mapToLong(Long::longValue).average().orElse(0))));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("months", months);
        b.put("income", r(inc.values().stream().mapToLong(Long::longValue).average().orElse(0)));
        b.put("spending", r(exp.values().stream().mapToLong(Long::longValue).average().orElse(0)));
        b.put("surplus", r(surplus));
        b.put("category_avg", catAvg);
        return b;
    }

    private static Map<String, Long> zeros(List<String> months) {
        Map<String, Long> m = new LinkedHashMap<>();
        months.forEach(k -> m.put(k, 0L));
        return m;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Long> cutPlan(long gap, Map<String, Object> catAvg) {
        Map<String, Double> room = new LinkedHashMap<>();
        for (String c : DISCRETIONARY) {
            Object v = catAvg.get(c);
            if (v != null && ((Number) v).longValue() != 0) room.put(c, ((Number) v).longValue() * MAX_CUT);
        }
        double total = room.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Long> out = new LinkedHashMap<>();
        if (total <= 0 || gap > total) return out;
        room.forEach((c, rm) -> { long cut = r(gap * rm / total); if (cut > 0) out.put(c, cut); });
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> assess(GoalIntent intent, String persona, String startMonth) {
        Map<String, Object> b = baseline(persona, 3);
        Map<String, Object> catAvg = (Map<String, Object>) b.get("category_avg");
        Map<String, Object> out = new LinkedHashMap<>();
        if ("save_amount".equals(intent.goalType())) {
            int months = intent.months();
            long target = intent.targetAmount();
            long required = (long) Math.ceil((double) target / months / 100) * 100;
            long cap = ((Number) b.get("surplus")).longValue();
            double roomTotal = DISCRETIONARY.stream().filter(catAvg::containsKey)
                    .mapToDouble(c -> ((Number) catAvg.get(c)).longValue() * MAX_CUT).sum();
            long gap = Math.max(0, required - Math.max(cap, 0));
            Map<String, Long> cuts = new LinkedHashMap<>();
            String status;
            if (required <= cap * COMFORT) status = "comfortable";
            else if (required <= cap) status = "tight";
            else if (gap <= roomTotal) { status = "needs_cut"; cuts = cutPlan(gap, catAvg); }
            else status = "infeasible";
            Integer alt = null;
            if ("infeasible".equals(status) && cap + roomTotal > 0) alt = (int) Math.ceil(target / (cap + roomTotal));
            List<Map<String, Object>> schedule = new ArrayList<>();
            long acc = 0;
            for (int i = 0; i < months; i++) {
                acc += required;
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("month", addMonths(startMonth, i));
                s.put("save", required);
                s.put("cumulative", Math.min(acc, target));
                schedule.add(s);
            }
            out.put("type", "save_amount"); out.put("status", status);
            out.put("required_monthly", required); out.put("monthly_capacity", cap); out.put("gap", gap);
            out.put("category_cuts", cuts); out.put("max_cuttable_total", r(roomTotal));
            out.put("alt_months", alt); out.put("schedule", schedule); out.put("baseline", b);
            return out;
        }
        long avg = catAvg.containsKey(intent.category()) ? ((Number) catAvg.get(intent.category())).longValue() : 0;
        long cut = Math.max(0, avg - intent.monthlyLimit());
        double pct = avg != 0 ? Aggregator.round(cut * 1.0 / avg * 100, 1) : 0.0;
        String status = cut == 0 ? "already_met" : pct <= 20 ? "easy" : pct <= 40 ? "stretch" : "hard";
        out.put("type", "cap_category"); out.put("status", status); out.put("category", intent.category());
        out.put("current_avg", avg); out.put("monthly_limit", intent.monthlyLimit());
        out.put("monthly_cut", cut); out.put("cut_pct", pct); out.put("yearly_saving", cut * 12); out.put("baseline", b);
        return out;
    }

    // ---------- 3) 설명 (LLM) + 숫자 검증 ----------
    public record Narration(GoalNarrative narrative, List<String> problems) {}

    public Narration narrate(String title, Map<String, Object> assessment) {
        Map<String, Object> slim = new LinkedHashMap<>(assessment);
        slim.remove("schedule");
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("headline", GeminiSchema.string(null));
        p.put("message", GeminiSchema.string("현실성 평가를 2~3문장으로"));
        p.put("tips", GeminiSchema.array(GeminiSchema.string(null), "실행 팁 2~3개"));
        String prompt = "목표: " + title + "\n\n평가 결과:\n" + om.writerWithDefaultPrettyPrinter().writeValueAsString(slim);
        Set<Double> allowed = numbers.allowed(slim);
        List<String> problems = List.of();
        GoalNarrative nar = null;
        for (int i = 0; i <= 2; i++) {
            String q = prompt + (problems.isEmpty() ? "" : "\n\n이전 응답의 문제:\n- " + String.join("\n- ", problems));
            nar = om.treeToValue(gemini.generateJson(NARR_SYSTEM, q, GeminiSchema.object(p), 0.4), GoalNarrative.class);
            List<String> texts = new ArrayList<>(List.of(nar.headline(), nar.message()));
            texts.addAll(nar.tips());
            problems = numbers.unknownNumbers(texts, allowed);
            if (problems.isEmpty()) return new Narration(nar, List.of());
        }
        return new Narration(nar, problems);
    }

    // ---------- 4) 저장·진행률 ----------
    public Map<String, Object> progress(JsonNode goal, String persona) {
        List<Tx> txs = data.get(persona).txs();
        String start = goal.path("start_month").asString();
        Set<String> months = new TreeSet<>();
        txs.stream().map(Tx::month).filter(m -> m.compareTo(start) >= 0).forEach(months::add);
        long inc = txs.stream().filter(t -> t.isIncome() && months.contains(t.month())).mapToLong(Tx::amount).sum();
        long exp = txs.stream().filter(t -> t.isExpense() && !Aggregator.SAVING_CATS.contains(t.category())
                && months.contains(t.month())).mapToLong(Tx::amount).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        if ("save_amount".equals(goal.path("type").asString())) {
            long saved = Math.max(0, inc - exp), target = goal.path("target_amount").asLong();
            out.put("saved", saved); out.put("target", target);
            out.put("pct", Aggregator.round(saved * 1.0 / target * 100, 1)); out.put("months_elapsed", months.size());
            return out;
        }
        String cat = goal.path("category").asString();
        long limit = goal.path("monthly_limit").asLong();
        List<Long> spent = months.stream().map(m -> txs.stream()
                .filter(t -> t.isExpense() && t.category().equals(cat) && t.month().equals(m)).mapToLong(Tx::amount).sum()).toList();
        out.put("monthly_spent", spent); out.put("limit", limit);
        out.put("months_within_limit", spent.stream().filter(s -> s <= limit).count());
        out.put("months_elapsed", months.size());
        return out;
    }

    /** UI 응답용: 저장된 목표 + 진행률 */
    public JsonNode loadWithProgress(String persona) {
        JsonNode saved = store.goal(persona);
        if (saved == null) return null;
        var node = (tools.jackson.databind.node.ObjectNode) saved.deepCopy();
        node.set("progress", om.valueToTree(progress(saved.path("goal"), persona)));
        return node;
    }

    public Map<String, Object> createGoal(String persona, String text) {
        Summary s = aggregator.summarize(persona);
        List<String> cats = s.categories().stream().map(Summary.CategoryStat::category).toList();
        GoalIntent intent = parseIntent(text, s.month(), cats);
        Map<String, Object> res = new LinkedHashMap<>();
        if (!"save_amount".equals(intent.goalType()) && !"cap_category".equals(intent.goalType())) {
            res.put("ok", false);
            res.put("ask", "어떤 목표인지 잘 모르겠어요. 모으고 싶은 금액과 기간, 또는 줄이고 싶은 지출을 알려주세요.");
            return res;
        }
        boolean saveMissing = "save_amount".equals(intent.goalType())
                && !(intent.targetAmount() != null && intent.targetAmount() != 0 && intent.months() != null && intent.months() != 0);
        boolean capMissing = "cap_category".equals(intent.goalType())
                && !(intent.category() != null && !intent.category().isEmpty() && intent.monthlyLimit() != null && intent.monthlyLimit() != 0);
        String clar = intent.needsClarification();
        if ((clar != null && !clar.isEmpty()) || saveMissing || capMissing) {
            res.put("ok", false);
            res.put("ask", clar != null && !clar.isEmpty() ? clar : "금액과 기간을 알려주세요.");
            return res;
        }
        if ("cap_category".equals(intent.goalType()) && !cats.contains(intent.category())) {
            res.put("ok", false);
            res.put("ask", "'" + intent.category() + "' 카테고리를 찾지 못했어요. 어떤 지출을 줄일까요?");
            return res;
        }
        String start = addMonths(s.month(), 1);
        Map<String, Object> a = assess(intent, persona, start);
        Narration n = narrate(intent.title(), a);
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("persona", persona); goal.put("type", intent.goalType()); goal.put("title", intent.title());
        goal.put("target_amount", intent.targetAmount()); goal.put("months", intent.months());
        goal.put("category", intent.category()); goal.put("monthly_limit", intent.monthlyLimit());
        goal.put("start_month", start); goal.put("status", a.get("status")); goal.put("plan", a);
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("goal", goal); saved.put("narrative", n.narrative()); saved.put("problems", n.problems());
        store.putGoal(persona, om.valueToTree(saved));
        res.put("ok", true);
        res.putAll(saved);
        return res;
    }
}
