package com.back.was.domain.ai;

import com.back.was.domain.coaching.GoalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성장 리포트 피드백 템플릿 (docs/AI_대체_문구.md 7장).
 *
 * <p>기대 문장은 그 문서의 문구에 명세 CHL-03 예시의 숫자를 채운 것이다 — 목표 50,000 /
 * 실적 35,000 / {@code gap} 15,000 / {@code before} 107,000 / 7일.
 */
class TemplateGrowthFeedbackGeneratorTest {

    private static final GrowthFeedbackCommand SUCCESS = new GrowthFeedbackCommand(
            true, GoalType.AMOUNT, 50_000, 35_000, 107_000, 7);

    private static final GrowthFeedbackCommand FAILED = new GrowthFeedbackCommand(
            false, GoalType.AMOUNT, 50_000, 56_000, 107_000, 7);

    /** AI_대체_문구 7장 "성공 (SUCCESS)" 세 문장. */
    private static final List<String> EXPECTED_SUCCESS = List.of(
            "정말 잘했어요! 목표보다 15,000원 덜 썼어요. 이 흐름이면 이번 달 예산에도 여유가 생겨요.",
            "챌린지 성공이에요 🎉 챌린지 전(107,000원)보다 확실히 줄었어요. 다음에는 조금 더 높은 목표에 도전해 볼까요?",
            "7일 동안 목표를 지켜냈어요. 스스로 조절하는 힘이 생기고 있어요!"
    );

    /** AI_대체_문구 7장 "실패 (FAILED)" 세 문장. */
    private static final List<String> EXPECTED_FAILED = List.of(
            "아쉽게 목표보다 6,000원 더 썼어요. 그래도 기록을 꾸준히 남긴 것만으로도 큰 한 걸음이에요.",
            "이번에는 목표를 넘었지만 괜찮아요. 목표를 조금 낮춰서 다시 도전해 봐요.",
            "쉽지 않았죠? 어떤 순간에 지출이 늘었는지 AI 코치와 함께 돌아보면 다음 챌린지에 도움이 될 거예요."
    );

    private final TemplateGrowthFeedbackGenerator generator = new TemplateGrowthFeedbackGenerator();

    @Test
    @DisplayName("성공 문구는 성공 묶음에서만 나온다")
    void fillsSuccessTemplates() {
        assertThat(generator.generate(SUCCESS)).isIn(EXPECTED_SUCCESS);
    }

    @Test
    @DisplayName("실패 문구는 실패 묶음에서만 나온다 — 성공 문구가 섞이면 사용자가 혼란스럽다")
    void fillsFailedTemplates() {
        assertThat(generator.generate(FAILED))
                .isIn(EXPECTED_FAILED)
                .isNotIn(EXPECTED_SUCCESS);
    }

    @Test
    @DisplayName("gap 은 절댓값이다 — 방향은 문구가 말한다 ('덜 썼어요' / '더 썼어요')")
    void gapIsAbsolute() {
        assertThat(SUCCESS.gap()).isEqualTo(15_000);
        assertThat(FAILED.gap()).isEqualTo(6_000);
    }

    @Test
    @DisplayName("COUNT 목표는 {gap}원 이 아니라 {gap}회 다 (7장 각주)")
    void countGoalSwitchesTheUnit() {
        GrowthFeedbackCommand countGoal = new GrowthFeedbackCommand(
                true, GoalType.COUNT, 2, 1, 107_000, 7);

        Set<String> feedbacks = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            feedbacks.add(generator.generate(countGoal));
        }

        assertThat(feedbacks).anySatisfy(feedback ->
                assertThat(feedback).contains("목표보다 1회 덜 썼어요"));
        assertThat(feedbacks).noneSatisfy(feedback ->
                assertThat(feedback).contains("1원"));
        // {before} 는 금액이라 COUNT 목표에서도 원이다 (각주는 {gap} 만 바꾼다)
        assertThat(feedbacks).anySatisfy(feedback ->
                assertThat(feedback).contains("챌린지 전(107,000원)"));
    }

    @Test
    @DisplayName("치환되지 않은 변수가 남지 않는다 — 템플릿에 새 변수를 적으면 여기서 걸린다")
    void leavesNoPlaceholder() {
        for (int i = 0; i < 100; i++) {
            assertThat(generator.generate(SUCCESS)).doesNotContain("{", "}");
            assertThat(generator.generate(FAILED)).doesNotContain("{", "}");
        }
    }

    @Test
    @DisplayName("같은 상황에도 문구가 돌아간다 — 매번 같은 문장이면 사용자가 고장으로 느낀다")
    void picksRandomly() {
        Set<String> success = new HashSet<>();
        Set<String> failed = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            success.add(generator.generate(SUCCESS));
            failed.add(generator.generate(FAILED));
        }

        assertThat(success).containsExactlyInAnyOrderElementsOf(EXPECTED_SUCCESS);
        assertThat(failed).containsExactlyInAnyOrderElementsOf(EXPECTED_FAILED);
    }
}
