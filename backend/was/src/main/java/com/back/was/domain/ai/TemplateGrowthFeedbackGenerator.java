package com.back.was.domain.ai;

import com.back.was.domain.coaching.GoalType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code docs/AI_대체_문구.md} 7장 템플릿으로 성장 리포트 피드백을 만든다 — <b>Gemini 없이도
 * CHL-03 이 완결되는 이유</b>다.
 *
 * <p>Gemini 어댑터가 생기면 이 클래스는 <b>남는다</b>({@link GrowthFeedbackGenerator} 의
 * 설명 참고). {@code TemplateNudgeTextGenerator} ·
 * {@code TemplateCoachingOpeningGenerator} 와 같은 성격·같은 규칙이다: 상황별 문구가 여러
 * 개면 <b>무작위로 하나</b>, 금액에는 천 단위 쉼표.
 *
 * <p><b>{@code {gap}} 의 단위만 목표 유형에 따라 바뀐다</b> — 7장 각주: "횟수 목표
 * ({@code COUNT})일 때는 {@code {gap}원} 을 {@code {gap}회} 로 바꿔서 써요". 그래서 템플릿
 * 문자열에는 단위를 적지 않고 {@code {gap}} 치환값에 붙인다. {@code {before}} 는 금액이라
 * 항상 원이다.
 *
 * <p>문구는 <b>금융 상품 추천·투자 조언을 담지 않는다</b>(SRS 8.3-5). 문서의 문장을 그대로
 * 옮겼으니 바꿀 때는 문서를 먼저 고칠 것.
 */
@Component
public class TemplateGrowthFeedbackGenerator implements GrowthFeedbackGenerator {

    /** AI_대체_문구 7장 "성공 (SUCCESS)". */
    private static final List<String> SUCCESS_TEMPLATES = List.of(
            "정말 잘했어요! 목표보다 {gap} 덜 썼어요. 이 흐름이면 이번 달 예산에도 여유가 생겨요.",
            "챌린지 성공이에요 🎉 챌린지 전({before}원)보다 확실히 줄었어요. 다음에는 조금 더 높은 목표에 도전해 볼까요?",
            "{period}일 동안 목표를 지켜냈어요. 스스로 조절하는 힘이 생기고 있어요!"
    );

    /** AI_대체_문구 7장 "실패 (FAILED)". */
    private static final List<String> FAILED_TEMPLATES = List.of(
            "아쉽게 목표보다 {gap} 더 썼어요. 그래도 기록을 꾸준히 남긴 것만으로도 큰 한 걸음이에요.",
            "이번에는 목표를 넘었지만 괜찮아요. 목표를 조금 낮춰서 다시 도전해 봐요.",
            "쉽지 않았죠? 어떤 순간에 지출이 늘었는지 AI 코치와 함께 돌아보면 다음 챌린지에 도움이 될 거예요."
    );

    @Override
    public String generate(GrowthFeedbackCommand command) {
        List<String> templates = command.success() ? SUCCESS_TEMPLATES : FAILED_TEMPLATES;

        return fill(pick(templates), command);
    }

    private static String pick(List<String> templates) {
        return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
    }

    /**
     * 변수를 하나씩 명시적으로 치환한다 — 다른 템플릿 생성기와 같은 이유다. 치환 목록에 없는
     * 변수를 템플릿에 적으면 테스트가 "남은 중괄호"로 잡는다.
     */
    private static String fill(String template, GrowthFeedbackCommand command) {
        return template
                .replace("{gap}", gap(command))
                .replace("{before}", won(command.beforeAmount()))
                .replace("{period}", String.valueOf(command.periodDays()));
    }

    /** 7장 각주의 단위 전환 — {@code AMOUNT} 는 "15,000원", {@code COUNT} 는 "2회". */
    private static String gap(GrowthFeedbackCommand command) {
        String unit = command.goalType() == GoalType.AMOUNT ? "원" : "회";
        return won(command.gap()) + unit;
    }

    /** AI_대체_문구 머리말: 금액은 천 단위 쉼표 (107000 → {@code 107,000}). */
    private static String won(int amount) {
        return String.format("%,d", amount);
    }
}
