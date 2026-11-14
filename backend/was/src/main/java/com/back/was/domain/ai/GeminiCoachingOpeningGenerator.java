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
 * 코칭 첫 메시지(COACH-01)의 Gemini 구현. <b>이 포트는 실패하지 않는다</b> — 명세 COACH-01 은 에러를 정의하지
 * 않고 201 과 메시지 하나를 요구한다. 호출 실패·빈 응답·검증 안 된 숫자는 모두
 * {@link TemplateCoachingOpeningGenerator} 로 대체한다.
 */
@Primary
@Component
public class GeminiCoachingOpeningGenerator implements CoachingOpeningGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiCoachingOpeningGenerator.class);

    private static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("message", GeminiSchema.string("코칭 첫 메시지. 2문장 이내"));
        SCHEMA = GeminiSchema.object(p);
    }

    private final GeminiClient gemini;
    private final TemplateCoachingOpeningGenerator template;

    public GeminiCoachingOpeningGenerator(GeminiClient gemini, TemplateCoachingOpeningGenerator template) {
        this.gemini = gemini;
        this.template = template;
    }

    @Override
    public String generate(CoachingOpeningCommand command) {
        try {
            JsonNode r = gemini.generateJson(AiPrompts.COACHING_OPENING, data(command), SCHEMA, 0.7);
            String message = r.path("message").asString("").strip();
            if (message.isEmpty()) {
                return template.generate(command);
            }

            Set<Double> allowed = command.hasNudgeContext()
                    ? NumberGuard.allowed(command.nudge().spendingAmount(), command.nudge().averageAmount(),
                            command.nudge().difference())
                    : NumberGuard.allowed();
            if (!NumberGuard.unknownNumbers(message, allowed).isEmpty()) {
                log.warn("[COACH-01] 첫 메시지에 입력에 없던 숫자가 있어 템플릿 문구를 쓴다");
                return template.generate(command);
            }
            return message;
        } catch (RuntimeException e) {
            return template.generate(command);
        }
    }

    private static String data(CoachingOpeningCommand c) {
        // 이름은 사용자가 정한 값이라 따옴표로 감싸 데이터임을 분명히 한다.
        if (!c.hasNudgeContext()) {
            return "[데이터]\n사용자 이름: \"%s\"\n(넛지 없이 시작한 코칭)".formatted(c.username());
        }
        CoachingOpeningCommand.NudgeContext n = c.nudge();
        return "[데이터]\n사용자 이름: \"%s\"\n%s, 이번 주 %,d원, 평소 %,d원(%+,d원)".formatted(
                c.username(), n.categoryName(), n.spendingAmount(), n.averageAmount(), n.difference());
    }
}
