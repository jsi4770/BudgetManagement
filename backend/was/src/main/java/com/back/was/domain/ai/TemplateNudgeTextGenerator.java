package com.back.was.domain.ai;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code docs/AI_대체_문구.md} 1·2장 템플릿으로 넛지 문구를 만든다 — <b>Gemini 없이도
 * 후처리 5번이 완결되는 이유</b>다(서버_내부_처리 8장 "넛지 AI 문구: 템플릿 문구로 채움").
 *
 * <p>Gemini 어댑터가 생기면 이 클래스는 <b>남는다</b>. 새 어댑터가 실패·타임아웃 때 이
 * 구현에 위임해야 하고, 그게 명세가 요구하는 대체 경로다. TX-07 의
 * {@code UnavailableTransactionTextParser}("실제 어댑터가 오면 삭제")와 반대 성격이다.
 *
 * <p>문구는 상황별로 세 개씩이고 <b>무작위로 하나</b>를 고른다(AI_대체_문구 머리말: 매번
 * 같은 문장이면 사용자가 고장으로 느낀다). 금액은 천 단위 쉼표, 증가율은 소수점을 버린다 —
 * 둘 다 그 문서의 규칙이다.
 *
 * <p>문구 자체는 <b>금융 상품 추천·투자 조언을 담지 않는다</b>(SRS 8.3-5). 문서의 문장을
 * 그대로 옮겼으니 바꿀 때는 문서를 먼저 고칠 것.
 */
@Component
public class TemplateNudgeTextGenerator implements NudgeTextGenerator {

    /** AI_대체_문구 1장 — {@code NUDGE.AI_ANALYSIS}. */
    private static final List<String> ANALYSIS_TEMPLATES = List.of(
            "이번 주 {days}일 동안 {category}에 {amount}원을 썼어요. 평소 이맘때({avg}원)보다 {diff}원 많아요.",
            "이번 주 {category} 지출이 평소보다 {rate}% 늘었어요. {days}일 동안 {count}번 결제했어요.",
            "평소 이맘때는 {category}에 {avg}원 정도 썼는데, 이번 주는 벌써 {amount}원이에요."
    );

    /** AI_대체_문구 2장 — {@code NUDGE.AI_RECOMMENDATION}. */
    private static final List<String> RECOMMENDATION_TEMPLATES = List.of(
            "한 번에 확 줄이기보다, 이번 주 {category} 한도를 정해서 가볍게 시작해 봐요.",
            "남은 일주일 동안 {category} 지출을 평소 수준으로 맞춰 보는 건 어떨까요?",
            "AI 코치와 이야기하면서 나에게 맞는 {category} 목표를 함께 정해 봐요."
    );

    @Override
    public NudgeText generate(NudgeTextCommand command) {
        return new NudgeText(
                fill(pick(ANALYSIS_TEMPLATES), command),
                fill(pick(RECOMMENDATION_TEMPLATES), command));
    }

    private static String pick(List<String> templates) {
        return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
    }

    /**
     * 변수를 하나씩 명시적으로 치환한다.
     *
     * <p>{@code Map<String, Object>} 를 돌리는 방식이면 템플릿에 새 변수를 적어도 컴파일은
     * 통과하고 사용자 화면에 {@code {count}} 가 그대로 나간다. 여기서는 치환 목록에 없는
     * 변수를 쓰면 테스트가 "남은 중괄호"로 잡는다.
     */
    private static String fill(String template, NudgeTextCommand command) {
        return template
                .replace("{category}", command.categoryName())
                .replace("{amount}", won(command.spendingAmount()))
                .replace("{avg}", won(command.averageAmount()))
                .replace("{diff}", won(command.difference()))
                .replace("{rate}", String.valueOf(rate(command.growthRate())))
                .replace("{days}", String.valueOf(command.elapsedDays()))
                .replace("{count}", String.valueOf(command.transactionCount()));
    }

    /** AI_대체_문구 머리말: 금액은 천 단위 쉼표 (41000 → {@code 41,000}). */
    private static String won(int amount) {
        return String.format("%,d", amount);
    }

    /**
     * {@code {rate}} 는 <b>소수점 버림</b>이다(변수 목록: 68.33% → {@code 68}).
     * {@code DOWN} 은 0 쪽으로 버리는데, 넛지가 생겼다는 건 증가율이 양수라는 뜻이라
     * {@code FLOOR} 와 결과가 같다.
     */
    private static long rate(BigDecimal growthRate) {
        return growthRate.setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
