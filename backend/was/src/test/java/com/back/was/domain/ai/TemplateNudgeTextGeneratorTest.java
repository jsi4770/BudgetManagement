package com.back.was.domain.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 넛지 대체 문구 템플릿 (docs/AI_대체_문구.md 1·2장).
 *
 * <p>기대 문장은 그 문서의 문구에 docs/시연_데이터.md 6-1 의 배달 숫자를 채운 것이다 —
 * 금액 101,000 / 평균 60,000 / 차이 41,000 / 증가율 68.33% / 3일 / 4번.
 */
class TemplateNudgeTextGeneratorTest {

    private static final NudgeTextCommand DELIVERY_SPIKE = new NudgeTextCommand(
            "배달", 101_000, 60_000, new BigDecimal("68.33"), 3, 4);

    /** AI_대체_문구 1장의 세 문장. */
    private static final List<String> EXPECTED_ANALYSIS = List.of(
            "이번 주 3일 동안 배달에 101,000원을 썼어요. 평소 이맘때(60,000원)보다 41,000원 많아요.",
            "이번 주 배달 지출이 평소보다 68% 늘었어요. 3일 동안 4번 결제했어요.",
            "평소 이맘때는 배달에 60,000원 정도 썼는데, 이번 주는 벌써 101,000원이에요."
    );

    /** AI_대체_문구 2장의 세 문장. */
    private static final List<String> EXPECTED_RECOMMENDATION = List.of(
            "한 번에 확 줄이기보다, 이번 주 배달 한도를 정해서 가볍게 시작해 봐요.",
            "남은 일주일 동안 배달 지출을 평소 수준으로 맞춰 보는 건 어떨까요?",
            "AI 코치와 이야기하면서 나에게 맞는 배달 목표를 함께 정해 봐요."
    );

    private final TemplateNudgeTextGenerator generator = new TemplateNudgeTextGenerator();

    @Test
    @DisplayName("문서의 문구에 변수를 채워서 돌려준다 — 금액은 천 단위 쉼표, 증가율은 소수점 버림")
    void fillsTemplatesFromTheDocument() {
        NudgeText text = generator.generate(DELIVERY_SPIKE);

        assertThat(text.analysis()).isIn(EXPECTED_ANALYSIS);
        assertThat(text.recommendation()).isIn(EXPECTED_RECOMMENDATION);
    }

    @Test
    @DisplayName("치환되지 않은 변수가 남지 않는다 — 템플릿에 새 변수를 적으면 여기서 걸린다")
    void leavesNoPlaceholder() {
        for (int i = 0; i < 100; i++) {
            NudgeText text = generator.generate(DELIVERY_SPIKE);

            assertThat(text.analysis()).doesNotContain("{", "}");
            assertThat(text.recommendation()).doesNotContain("{", "}");
        }
    }

    @Test
    @DisplayName("같은 상황에도 문구가 돌아간다 — 매번 같은 문장이면 사용자가 고장으로 느낀다")
    void picksRandomly() {
        Set<String> analyses = new HashSet<>();
        Set<String> recommendations = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            NudgeText text = generator.generate(DELIVERY_SPIKE);
            analyses.add(text.analysis());
            recommendations.add(text.recommendation());
        }

        assertThat(analyses).containsExactlyInAnyOrderElementsOf(EXPECTED_ANALYSIS);
        assertThat(recommendations).containsExactlyInAnyOrderElementsOf(EXPECTED_RECOMMENDATION);
    }

    @Test
    @DisplayName("증가율은 반올림이 아니라 버림이다 (68.99% → 68%)")
    void truncatesGrowthRate() {
        NudgeTextCommand command = new NudgeTextCommand(
                "배달", 101_000, 60_000, new BigDecimal("68.99"), 3, 4);

        Set<String> analyses = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            analyses.add(generator.generate(command).analysis());
        }

        assertThat(analyses).anySatisfy(analysis ->
                assertThat(analysis).contains("평소보다 68% 늘었어요"));
        assertThat(analyses).noneSatisfy(analysis ->
                assertThat(analysis).contains("69%"));
    }

    @Test
    @DisplayName("백만 원대 금액도 쉼표가 제대로 들어간다")
    void formatsLargeAmounts() {
        NudgeTextCommand command = new NudgeTextCommand(
                "식비", 1_234_567, 200_000, new BigDecimal("517.28"), 7, 21);

        Set<String> analyses = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            analyses.add(generator.generate(command).analysis());
        }

        assertThat(analyses).anySatisfy(analysis ->
                assertThat(analysis).contains("1,234,567원"));
        assertThat(analyses).anySatisfy(analysis ->
                assertThat(analysis).contains("1,034,567원 많아요"));
    }
}
