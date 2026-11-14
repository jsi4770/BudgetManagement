package com.back.was.domain.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코칭 첫 메시지 대체 문구 (docs/AI_대체_문구.md 3장).
 *
 * <p>기대 문장은 그 문서의 문구에 docs/시연_데이터.md 6-1 의 배달 숫자를 채운 것이다 —
 * 금액 101,000 / 평균 60,000 / 차이 41,000. 명세 COACH-01 응답 예시의 첫 메시지가 바로
 * 첫 번째 문장이다.
 */
class TemplateCoachingOpeningGeneratorTest {

    private static final CoachingOpeningCommand FROM_NUDGE =
            CoachingOpeningCommand.fromNudge("홍길동", "배달", 101_000, 60_000);

    private static final CoachingOpeningCommand WITHOUT_NUDGE =
            CoachingOpeningCommand.withoutNudge("홍길동");

    /** AI_대체_문구 3장 "넛지에서 시작한 경우". */
    private static final List<String> EXPECTED_FROM_NUDGE = List.of(
            "홍길동님, 이번 주 배달 지출이 101,000원으로 평소 이맘때(60,000원)보다 늘었어요. 어떤 점이 제일 신경 쓰이세요?",
            "홍길동님, 이번 주에 배달에 평소보다 41,000원을 더 썼어요. 요즘 특별한 이유가 있었나요?"
    );

    /** AI_대체_문구 3장 "넛지 없이 시작한 경우". */
    private static final List<String> EXPECTED_WITHOUT_NUDGE = List.of(
            "홍길동님, 안녕하세요! 요즘 소비에서 가장 줄이고 싶은 부분이 있나요?",
            "홍길동님, 반가워요. 이번 주 소비 중에 아쉬웠던 부분이 있으면 편하게 이야기해 주세요."
    );

    private final TemplateCoachingOpeningGenerator generator = new TemplateCoachingOpeningGenerator();

    @Test
    @DisplayName("넛지에서 시작하면 그 카드의 숫자를 인용한다 — 금액은 천 단위 쉼표")
    void quotesTheNudgeNumbers() {
        assertThat(generator.generate(FROM_NUDGE)).isIn(EXPECTED_FROM_NUDGE);
    }

    @Test
    @DisplayName("넛지 없이 시작하면 숫자 없는 인사말이다 — 인용할 카드가 없다")
    void greetsWithoutNumbers() {
        assertThat(generator.generate(WITHOUT_NUDGE)).isIn(EXPECTED_WITHOUT_NUDGE);
    }

    @Test
    @DisplayName("치환되지 않은 변수가 남지 않는다 — 템플릿에 새 변수를 적으면 여기서 걸린다")
    void leavesNoPlaceholder() {
        for (int i = 0; i < 100; i++) {
            assertThat(generator.generate(FROM_NUDGE)).doesNotContain("{", "}");
            assertThat(generator.generate(WITHOUT_NUDGE)).doesNotContain("{", "}");
        }
    }

    @Test
    @DisplayName("같은 상황에도 문구가 돌아간다 — 매번 같은 문장이면 사용자가 고장으로 느낀다")
    void picksRandomly() {
        Set<String> fromNudge = new HashSet<>();
        Set<String> withoutNudge = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            fromNudge.add(generator.generate(FROM_NUDGE));
            withoutNudge.add(generator.generate(WITHOUT_NUDGE));
        }

        assertThat(fromNudge).containsExactlyInAnyOrderElementsOf(EXPECTED_FROM_NUDGE);
        assertThat(withoutNudge).containsExactlyInAnyOrderElementsOf(EXPECTED_WITHOUT_NUDGE);
    }

    @Test
    @DisplayName("백만 원대 금액도 쉼표가 제대로 들어간다")
    void formatsLargeAmounts() {
        CoachingOpeningCommand command =
                CoachingOpeningCommand.fromNudge("이영희", "식비", 1_234_567, 200_000);

        Set<String> openings = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            openings.add(generator.generate(command));
        }

        assertThat(openings).anySatisfy(opening ->
                assertThat(opening).contains("1,234,567원"));
        assertThat(openings).anySatisfy(opening ->
                assertThat(opening).contains("1,034,567원을 더 썼어요"));
    }
}
