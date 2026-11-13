package com.back.was.coaching;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.ai.gemini.GeminiMessage;
import com.back.was.ai.gemini.GeminiSchema;
import com.back.was.analysis.CategoryComparison;
import com.back.was.analysis.MonthlySummaryService.MonthlySummary;
import com.back.was.category.CategoryInfo;
import com.back.was.category.CategoryProvider;
import com.back.was.common.TimeUtil;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 재무 코칭 (COACH-03 메시지 → AI 응답, SFR-022~026) + 성장 리포트 피드백 (SFR-035).
 *
 * COACH API(요청·응답 필드, 대화 저장)는 명세 상세가 없어서 여기서는 "AI 부분"만 제공합니다.
 * 코칭 컨트롤러/서비스에서 아래처럼 호출하세요.
 *
 *   String ctx = coachingAi.buildContext(week, nudgeAiAnalysis, monthlySummary);  // 코칭 시작 시 1번 만들어 저장
 *   CoachReply r = coachingAi.reply(ctx, 이전대화들, 사용자메시지);              // COACH-03
 *   // r.proposedGoal() 은 COACH-04 확정 전까지 저장하지 않음
 *
 * Gemini 가 실패해도 예외 대신 대체 메시지(fallback=true)를 돌려준다 → 명세 "코칭은 실패 시에도 200"
 */
@Service
public class CoachingAiService {

    private static final Logger log = LoggerFactory.getLogger(CoachingAiService.class);
    private static final int HISTORY_LIMIT = 12;        // 최근 12턴만 보냄 (토큰 절약)

    private static final Map<String, Object> REPLY_SCHEMA;
    private static final Map<String, Object> FEEDBACK_SCHEMA;

    static {
        Map<String, Map<String, Object>> goal = new LinkedHashMap<>();
        goal.put("categoryId", GeminiSchema.integer("목표 대상 지출 카테고리 id"));
        goal.put("targetAmount", GeminiSchema.integer("기간 동안 쓸 최대 금액(원). 1,000원 단위"));
        goal.put("periodDays", GeminiSchema.integer("챌린지 기간(일). 7 또는 14 권장"));
        goal.put("reason", GeminiSchema.string("이 금액을 제안한 근거 한 문장 (데이터의 평균·이번 주 금액 인용)"));

        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("reply", GeminiSchema.string("사용자에게 보낼 답변. 3~5문장"));
        p.put("proposedGoal", GeminiSchema.nullable(GeminiSchema.object(goal)));
        REPLY_SCHEMA = GeminiSchema.object(p);

        Map<String, Map<String, Object>> f = new LinkedHashMap<>();
        f.put("feedback", GeminiSchema.string("챌린지 결과 피드백. 3문장 이내"));
        FEEDBACK_SCHEMA = GeminiSchema.object(f);
    }

    private final GeminiClient gemini;
    private final CategoryProvider categories;

    public CoachingAiService(GeminiClient gemini, CategoryProvider categories) {
        this.gemini = gemini;
        this.categories = categories;
    }

    // ------------------------------------------------------------------ 컨텍스트

    /**
     * 서버 데이터 → 프롬프트용 사실 요약. (모델은 이 숫자만 인용)
     *
     * @param week           ANL-03 WEEK 결과
     * @param nudgeAnalysis  코칭을 시작한 넛지의 aiAnalysis (없으면 null)
     * @param monthly        ANL-01 결과 (없으면 null)
     */
    public String buildContext(CategoryComparison week, String nudgeAnalysis, MonthlySummary monthly) {
        StringBuilder sb = new StringBuilder();
        sb.append("분석 기간: ").append(week.startDate()).append(" ~ ").append(week.endDate())
                .append(" (이번 주, 월세 제외)\n");
        if (!week.dataSufficient()) {
            sb.append("가입한 지 4주가 안 되어 평소 평균은 아직 없음\n");
        }
        for (CategoryComparison.Item i : week.items()) {
            sb.append(String.format("- %s(id=%d): 이번 주 %,d원, 평소 %s, 증가율 %s, 이상치 %s%n",
                    i.category().name(), i.category().id(), i.amount(),
                    i.averageAmount() == null ? "데이터 부족" : String.format("%,d원", i.averageAmount()),
                    i.growthRate() == null ? "-" : i.growthRate() + "%",
                    i.isAnomaly() ? "예" : "아니오"));
        }
        if (nudgeAnalysis != null && !nudgeAnalysis.isBlank()) {
            sb.append("코칭 시작 계기(넛지): ").append(nudgeAnalysis).append("\n");
        }
        if (monthly != null && monthly.budget() != null) {
            sb.append(String.format("이번 달 예산 %,d원 중 %,d원 사용 (%s%%)%n",
                    monthly.budget(), monthly.totalExpense(), monthly.budgetUsageRate()));
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ 대화

    /**
     * COACH-03 메시지 1회.
     *
     * @param context     buildContext() 결과
     * @param history     이전 대화 (user/model 번갈아, 오래된 순). 첫 메시지는 빈 리스트
     * @param userMessage 이번 사용자 메시지
     */
    public CoachReply reply(String context, List<GeminiMessage> history, String userMessage) {
        List<GeminiMessage> messages = new ArrayList<>();
        // 데이터는 대화 맨 앞에 한 번 넣고, AI 가 확인했다는 턴을 붙여 user/model 순서를 맞춤
        messages.add(GeminiMessage.user("[소비 데이터]\n" + context));
        messages.add(GeminiMessage.model("{\"reply\": \"확인했어요.\", \"proposedGoal\": null}"));
        int from = Math.max(0, history.size() - HISTORY_LIMIT);
        messages.addAll(history.subList(from, history.size()));
        messages.add(GeminiMessage.user(userMessage));

        try {
            JsonNode r = gemini.generateJson(CoachingPrompts.COACH_SYSTEM, messages, REPLY_SCHEMA, 0.7);
            String reply = r.path("reply").asString("").strip();
            if (reply.isEmpty()) {
                return new CoachReply(CoachingPrompts.COACH_FALLBACK, null, true);
            }
            return new CoachReply(reply, validateGoal(r.path("proposedGoal")), false);
        } catch (Exception e) {
            log.warn("[COACH] Gemini 실패 → 대체 메시지: {}", e.getMessage());
            return new CoachReply(CoachingPrompts.COACH_FALLBACK, null, true);
        }
    }

    /**
     * 모델이 낸 목표를 서버 규칙으로 검증·보정. 이상하면 null (목표 없음 처리).
     * - 지출 카테고리, 월세 아님 / 금액 > 0, 1,000원 단위 / 기간 1~31일 (아니면 7일)
     */
    GoalProposal validateGoal(JsonNode g) {
        if (g == null || g.isMissingNode() || g.isNull()) return null;
        if (!g.path("categoryId").isIntegralNumber() || !g.path("targetAmount").isNumber()) return null;

        Optional<CategoryInfo> cat = categories.findById(g.path("categoryId").asLong());
        if (cat.isEmpty() || !cat.get().isExpense() || categories.isRent(cat.get().id())) return null;

        long target = Math.round(g.path("targetAmount").asDouble() / 1000.0) * 1000;
        if (target <= 0) return null;

        int days = g.path("periodDays").asInt(7);
        if (days < 1 || days > 31) days = 7;

        LocalDate start = TimeUtil.today();
        return new GoalProposal(cat.get().id(), cat.get().name(), target,
                start.toString(), start.plusDays(days - 1L).toString(),
                g.path("reason").asString(""));
    }

    // ------------------------------------------------------------------ 성장 리포트

    /** SFR-035 챌린지 결과 피드백. 실패 시 템플릿 문구 */
    public String growthFeedback(ChallengeResultFacts f) {
        String data = String.format(
                "[결과]%n%s 챌린지 %s ~ %s, 목표 %,d원 이하, 실제 %,d원, %s%n챌린지 전 주간 평균 %s, 챌린지 중 주간 평균 %s",
                f.categoryName(), f.startDate(), f.endDate(), f.targetAmount(), f.actualAmount(),
                f.achieved() ? "성공" : "실패",
                f.beforeWeeklyAvg() == null ? "-" : String.format("%,d원", f.beforeWeeklyAvg()),
                f.duringWeeklyAvg() == null ? "-" : String.format("%,d원", f.duringWeeklyAvg()));
        try {
            JsonNode r = gemini.generateJson(CoachingPrompts.FEEDBACK_SYSTEM, data, FEEDBACK_SCHEMA, 0.6);
            String text = r.path("feedback").asString("").strip();
            if (!text.isEmpty()) return text;
        } catch (Exception e) {
            log.warn("[REPORT] Gemini 실패 → 대체 문구: {}", e.getMessage());
        }
        return f.achieved()
                ? String.format("%s 목표를 지켜냈어요! 이번에 만든 습관을 다음 주에도 이어가 봐요.", f.categoryName())
                : String.format("%s 목표에는 조금 못 미쳤지만 기록을 이어간 것 자체가 의미 있어요. 다음엔 목표를 살짝 여유 있게 잡아봐요.",
                        f.categoryName());
    }
}
