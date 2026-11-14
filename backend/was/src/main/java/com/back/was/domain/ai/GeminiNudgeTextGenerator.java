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
 * 넛지 문구 생성의 Gemini 구현 (NDG-02 {@code aiAnalysis}·{@code aiRecommendation}).
 *
 * <p><b>이 포트는 실패하지 않는다</b>(명세 0장 4) 각주). Gemini 가 죽거나 응답이 비었거나, 서버가 준 적 없는
 * 숫자가 문구에 끼어들면 {@link TemplateNudgeTextGenerator} 로 대체한다 — 그래서 호출하는 쪽에
 * {@code try/catch} 가 없다. 템플릿은 지우지 않는다: 이 구현의 "실패 시 템플릿"이다.
 */
@Primary
@Component
public class GeminiNudgeTextGenerator implements NudgeTextGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiNudgeTextGenerator.class);

    private static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("analysis", GeminiSchema.string("이번 주 소비 현황과 늘어난 정도. 1~2문장"));
        p.put("recommendation", GeminiSchema.string("이번 주 안에 해볼 수 있는 작은 행동 하나. 1~2문장"));
        SCHEMA = GeminiSchema.object(p);
    }

    private final GeminiClient gemini;
    private final TemplateNudgeTextGenerator template;

    public GeminiNudgeTextGenerator(GeminiClient gemini, TemplateNudgeTextGenerator template) {
        this.gemini = gemini;
        this.template = template;
    }

    @Override
    public NudgeText generate(NudgeTextCommand command) {
        try {
            JsonNode r = gemini.generateJson(AiPrompts.NUDGE_TEXT, data(command), SCHEMA, 0.6);
            String analysis = r.path("analysis").asString("").strip();
            String recommendation = r.path("recommendation").asString("").strip();
            if (analysis.isEmpty() || recommendation.isEmpty()) {
                log.warn("[NDG-02] Gemini 응답이 비어 템플릿 문구를 쓴다");
                return template.generate(command);
            }

            Set<Double> allowed = NumberGuard.allowed(command.spendingAmount(), command.averageAmount(),
                    command.difference(), command.growthRate(), command.elapsedDays(), command.transactionCount());
            if (!NumberGuard.unknownNumbers(analysis + " " + recommendation, allowed).isEmpty()) {
                log.warn("[NDG-02] 문구에 입력에 없던 숫자가 있어 템플릿 문구를 쓴다");
                return template.generate(command);
            }
            return new NudgeText(analysis, recommendation);
        } catch (RuntimeException e) {
            // 한도·장애·타임아웃·파싱 실패 모두. 원인은 GeminiErrors 가 이미 남겼다.
            return template.generate(command);
        }
    }

    private static String data(NudgeTextCommand c) {
        return "[데이터]\n%s, 이번 주 %d일간 %d회 %,d원, 평소 %,d원(%+,d원, %s%%)".formatted(
                c.categoryName(), c.elapsedDays(), c.transactionCount(), c.spendingAmount(),
                c.averageAmount(), c.difference(),
                c.growthRate() == null ? "-" : c.growthRate().toPlainString());
    }
}
