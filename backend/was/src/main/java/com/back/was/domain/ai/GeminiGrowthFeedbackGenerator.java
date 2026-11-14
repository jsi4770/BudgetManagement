package com.back.was.domain.ai;

import com.back.was.domain.ai.gemini.GeminiClient;
import com.back.was.domain.ai.gemini.GeminiSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 성장 리포트 피드백({@code GROWTH_REPORT.FEEDBACK})의 Gemini 구현. ERD 10장이 "생성 실패 시 결과별 템플릿
 * 문구"로 정했으므로 <b>실패하지 않는다</b> — 실패·빈 응답·검증 안 된 숫자는
 * {@link TemplateGrowthFeedbackGenerator} 로 대체한다.
 */
@Primary
@Component
public class GeminiGrowthFeedbackGenerator implements GrowthFeedbackGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiGrowthFeedbackGenerator.class);

    private static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("feedback", GeminiSchema.string("챌린지 결과 피드백. 3문장 이내"));
        SCHEMA = GeminiSchema.object(p);
    }

    private final GeminiClient gemini;
    private final TemplateGrowthFeedbackGenerator template;

    public GeminiGrowthFeedbackGenerator(GeminiClient gemini, TemplateGrowthFeedbackGenerator template) {
        this.gemini = gemini;
        this.template = template;
    }

    @Override
    public String generate(GrowthFeedbackCommand command) {
        try {
            JsonNode r = gemini.generateJson(AiPrompts.GROWTH_FEEDBACK, data(command), SCHEMA, 0.6);
            String feedback = r.path("feedback").asString("").strip();
            if (feedback.isEmpty()) {
                return template.generate(command);
            }

            Set<Double> allowed = NumberGuard.allowed(command.goal(), command.spending(),
                    command.beforeAmount(), command.periodDays(), command.gap());
            if (!NumberGuard.unknownNumbers(feedback, allowed).isEmpty()) {
                log.warn("[CHL-03] 피드백에 입력에 없던 숫자가 있어 템플릿 문구를 쓴다");
                return template.generate(command);
            }
            if (mentionsOtherCategory(feedback, command.categoryName())) {
                log.warn("[CHL-03] 피드백이 챌린지와 다른 카테고리를 말해 템플릿 문구를 쓴다");
                return template.generate(command);
            }
            return feedback;
        } catch (RuntimeException e) {
            return template.generate(command);
        }
    }

    /** 카테고리는 ERD 4장의 고정 시드라서 이름 목록을 코드에 둔다. 챌린지의 카테고리 외 이름이 나오면 환각이다. */
    private static final java.util.List<String> CATEGORY_NAMES =
            java.util.List.of("식비", "배달", "카페", "교통", "월세");

    static boolean mentionsOtherCategory(String feedback, String categoryName) {
        return CATEGORY_NAMES.stream()
                .anyMatch(name -> !name.equals(categoryName) && feedback.contains(name));
    }

    private static String data(GrowthFeedbackCommand c) {
        String unit = c.goalType() == com.back.was.domain.coaching.GoalType.AMOUNT ? "원" : "회";
        String category = c.categoryName() == null ? "" : "카테고리 " + c.categoryName() + ", ";
        return "[결과]\n" + category + "%s, 목표 %,d%s 이내, 실제 %,d%s, 목표와의 차이 %,d%s, 챌린지 직전 같은 기간 %,d원, 기간 %d일".formatted(
                c.success() ? "성공" : "미달성", c.goal(), unit, c.spending(), unit, c.gap(), unit,
                c.beforeAmount(), c.periodDays());
    }
}
