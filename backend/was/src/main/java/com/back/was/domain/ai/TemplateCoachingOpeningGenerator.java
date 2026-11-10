package com.back.was.domain.ai;

import com.back.was.domain.ai.CoachingOpeningCommand.NudgeContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code docs/AI_대체_문구.md} 3장 템플릿으로 코칭 첫 메시지를 만든다 — <b>Gemini 없이도
 * COACH-01 이 완결되는 이유</b>다.
 *
 * <p>Gemini 어댑터가 생기면 이 클래스는 <b>남는다</b>({@link CoachingOpeningGenerator} 의
 * 설명 참고). {@code TemplateNudgeTextGenerator} 와 같은 성격·같은 규칙이다: 상황별 문구가
 * 여러 개면 <b>무작위로 하나</b>를 고르고(매번 같은 문장이면 사용자가 고장으로 느낀다),
 * 금액에는 천 단위 쉼표를 붙인다.
 *
 * <p>문구는 <b>금융 상품 추천·투자 조언을 담지 않는다</b>(SRS 8.3-5). 문서의 문장을 그대로
 * 옮겼으니 바꿀 때는 문서를 먼저 고칠 것.
 */
@Component
public class TemplateCoachingOpeningGenerator implements CoachingOpeningGenerator {

    /** AI_대체_문구 3장 "넛지에서 시작한 경우". */
    private static final List<String> FROM_NUDGE_TEMPLATES = List.of(
            "{username}님, 이번 주 {category} 지출이 {amount}원으로 평소 이맘때({avg}원)보다 늘었어요. 어떤 점이 제일 신경 쓰이세요?",
            "{username}님, 이번 주에 {category}에 평소보다 {diff}원을 더 썼어요. 요즘 특별한 이유가 있었나요?"
    );

    /** AI_대체_문구 3장 "넛지 없이 시작한 경우". */
    private static final List<String> WITHOUT_NUDGE_TEMPLATES = List.of(
            "{username}님, 안녕하세요! 요즘 소비에서 가장 줄이고 싶은 부분이 있나요?",
            "{username}님, 반가워요. 이번 주 소비 중에 아쉬웠던 부분이 있으면 편하게 이야기해 주세요."
    );

    @Override
    public String generate(CoachingOpeningCommand command) {
        return command.hasNudgeContext()
                ? fillFromNudge(pick(FROM_NUDGE_TEMPLATES), command.username(), command.nudge())
                : fillUsername(pick(WITHOUT_NUDGE_TEMPLATES), command.username());
    }

    private static String pick(List<String> templates) {
        return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
    }

    /**
     * 변수를 하나씩 명시적으로 치환한다 — {@code TemplateNudgeTextGenerator} 와 같은 이유다.
     * 치환 목록에 없는 변수를 템플릿에 적으면 테스트가 "남은 중괄호"로 잡는다.
     */
    private static String fillFromNudge(String template, String username, NudgeContext nudge) {
        return fillUsername(template, username)
                .replace("{category}", nudge.categoryName())
                .replace("{amount}", won(nudge.spendingAmount()))
                .replace("{avg}", won(nudge.averageAmount()))
                .replace("{diff}", won(nudge.difference()));
    }

    private static String fillUsername(String template, String username) {
        return template.replace("{username}", username);
    }

    /** AI_대체_문구 머리말: 금액은 천 단위 쉼표 (41000 → {@code 41,000}). */
    private static String won(int amount) {
        return String.format("%,d", amount);
    }
}
