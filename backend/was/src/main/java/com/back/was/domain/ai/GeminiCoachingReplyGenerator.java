package com.back.was.domain.ai;

import com.back.was.domain.ai.gemini.GeminiClient;
import com.back.was.domain.ai.gemini.GeminiErrors;
import com.back.was.domain.ai.gemini.GeminiMessage;
import com.back.was.domain.ai.gemini.GeminiSchema;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.coaching.MessageRole;
import com.back.was.global.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * COACH-03 대화 응답의 Gemini 구현.
 *
 * <p><b>이 포트는 실패를 숨기지 않는다</b> — 호출자({@code CoachingService})가 예외를 잡아 규칙 기반 대체 응답에
 * {@code isFallback: true} 를 붙여 200 으로 내린다(명세 COACH-03). 그래서 여기서는 실패를 그대로 던진다:
 * 호출 실패·한도·타임아웃 → {@code AI_UNAVAILABLE}, 읽을 수 없거나 비었거나 검증을 못 넘긴 응답 →
 * {@code AI_PARSE_FAILED}.
 *
 * <p>답변의 숫자는 대화에 이미 나온 값과 제안한 목표 값만 허용한다({@link NumberGuard}). 어긋나면 어떤 숫자가
 * 문제인지 알려 한 번 다시 시킨다. 그래도 어긋나면 던진다.
 *
 * <p>제안({@code proposedGoal})은 형태만 정리한다 — 카테고리 존재·ERD 7장 조건의 최종 검증은 저장하는 쪽
 * ({@code CoachingService#applyProposal}) 몫이다. 알아볼 수 없는 제안은 버리고 메시지만 돌려준다.
 */
@Primary
@Component
public class GeminiCoachingReplyGenerator implements CoachingReplyGenerator {

    private static final Set<Integer> PERIODS = Set.of(7, 14, 30);
    private static final int DEFAULT_PERIOD_DAYS = 7;

    private static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> goal = new LinkedHashMap<>();
        goal.put("categoryId", GeminiSchema.integer("목표 대상 지출 카테고리 id (목록 중 하나)"));
        goal.put("title", GeminiSchema.string("목표 제목. 예: 배달 2회 이하"));
        goal.put("goalType", GeminiSchema.string("AMOUNT 또는 COUNT"));
        goal.put("goalAmount", GeminiSchema.nullable(GeminiSchema.integer("AMOUNT 일 때 목표 금액(원). 아니면 null")));
        goal.put("goalCount", GeminiSchema.nullable(GeminiSchema.integer("COUNT 일 때 목표 횟수. 아니면 null")));
        goal.put("periodDays", GeminiSchema.integer("기간(일): 7, 14, 30 중 하나"));

        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("message", GeminiSchema.string("사용자에게 보낼 답변. 3~5문장"));
        p.put("proposedGoal", GeminiSchema.nullable(GeminiSchema.object(goal)));
        SCHEMA = GeminiSchema.object(p);
    }

    private final GeminiClient gemini;

    public GeminiCoachingReplyGenerator(GeminiClient gemini) {
        this.gemini = gemini;
    }

    /** @throws BusinessException {@code AI_UNAVAILABLE} / {@code AI_PARSE_FAILED} */
    @Override
    public CoachingReply reply(CoachingReplyCommand command) {
        String system = AiPrompts.COACHING_REPLY
                .replace("{USERNAME}", "\"" + command.username() + "\"")
                .replace("{CATEGORIES}", categoryLines(command.categories()));
        List<GeminiMessage> messages = toMessages(command.conversation());

        CoachingReply reply = parse(gemini.generateJson(system, messages, SCHEMA, 0.5), command);
        List<String> bad = unknownNumbers(reply, command);
        if (bad.isEmpty()) {
            return reply;
        }

        // 한 번만 다시 시킨다 — 어떤 숫자가 근거 없는지 알려 준다.
        List<GeminiMessage> retry = new ArrayList<>(messages);
        retry.add(GeminiMessage.model(reply.message()));
        retry.add(GeminiMessage.user("방금 답변의 숫자 " + String.join(", ", bad)
                + " 는 대화에 나온 적 없는 값이야. 대화에 나온 숫자와 제안하는 목표 값만 써서 다시 답해줘."));
        reply = parse(gemini.generateJson(system, retry, SCHEMA, 0.5), command);
        if (!unknownNumbers(reply, command).isEmpty()) {
            throw GeminiErrors.parseFailed("코칭 답변에 근거 없는 숫자가 남았다");
        }
        return reply;
    }

    private static CoachingReply parse(JsonNode r, CoachingReplyCommand command) {
        String message = r.path("message").asString("").strip();
        if (message.isEmpty()) {
            throw GeminiErrors.parseFailed("코칭 답변이 비어 있다");
        }
        return new CoachingReply(message, parseGoal(r.path("proposedGoal"), command.categories()));
    }

    /** 형태만 정리한다. 알아볼 수 없으면 {@code null} — 메시지는 그대로 내려간다. */
    private static ProposedGoal parseGoal(JsonNode g, List<CategoryResponse> categories) {
        if (!g.isObject()) {
            return null;
        }
        JsonNode id = g.path("categoryId");
        if (!id.isIntegralNumber() || categories.stream().noneMatch(c -> c.id() == id.asLong())
                || id.asLong() == RentCategory.ID) {
            return null;
        }
        GoalType type;
        try {
            type = GoalType.valueOf(g.path("goalType").asString("").strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Integer amount = positiveInt(g.path("goalAmount"));
        Integer count = positiveInt(g.path("goalCount"));
        if ((type == GoalType.AMOUNT && amount == null) || (type == GoalType.COUNT && count == null)) {
            return null;
        }
        Integer period = positiveInt(g.path("periodDays"));
        if (period == null || !PERIODS.contains(period)) {
            period = DEFAULT_PERIOD_DAYS;
        }
        String title = g.path("title").asString("").strip();
        return new ProposedGoal(id.asLong(), title.isEmpty() ? null : title, type,
                type == GoalType.AMOUNT ? amount : null, type == GoalType.COUNT ? count : null, period);
    }

    private static Integer positiveInt(JsonNode n) {
        return n.isIntegralNumber() && n.asLong() >= 1 && n.asLong() <= Integer.MAX_VALUE ? (int) n.asLong() : null;
    }

    private static List<String> unknownNumbers(CoachingReply reply, CoachingReplyCommand command) {
        Set<Double> allowed = NumberGuard.allowed();
        NumberGuard.addNumbersIn(command.conversation().stream().map(CoachingTurn::content).toList(), allowed);
        ProposedGoal goal = reply.proposal();
        if (goal != null) {
            allowed.addAll(NumberGuard.allowed(goal.goalAmount(), goal.goalCount(), goal.periodDays()));
        }
        return NumberGuard.unknownNumbers(reply.message(), allowed);
    }

    private static String categoryLines(List<CategoryResponse> categories) {
        return categories.stream()
                .filter(c -> c.id() != RentCategory.ID)
                .map(c -> "id=" + c.id() + " " + c.name())
                .collect(Collectors.joining(", "));
    }

    /**
     * 대화를 Gemini 형식으로. 첫 턴이 AI(코칭 첫 메시지)이므로 user 로 시작하게 앞에 한 줄을 붙이고,
     * 같은 역할이 이어지면 합친다.
     */
    private static List<GeminiMessage> toMessages(List<CoachingTurn> conversation) {
        List<GeminiMessage> out = new ArrayList<>();
        if (conversation.isEmpty() || conversation.get(0).role() == MessageRole.AI) {
            out.add(GeminiMessage.user("(코칭을 시작했어요)"));
        }
        for (CoachingTurn turn : conversation) {
            String role = turn.role() == MessageRole.USER ? "user" : "model";
            if (!out.isEmpty() && out.get(out.size() - 1).role().equals(role)) {
                GeminiMessage last = out.remove(out.size() - 1);
                out.add(new GeminiMessage(role, last.text() + "\n" + turn.content()));
            } else {
                out.add(new GeminiMessage(role, turn.content()));
            }
        }
        return out;
    }
}
