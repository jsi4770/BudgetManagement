package com.back.was.domain.ai;

import com.back.was.domain.ai.AiTestFixtures.ScriptedGemini;
import com.back.was.domain.coaching.GoalType;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 넛지 문구·코칭 첫 메시지·성장 리포트 피드백 — <b>실패하지 않는 포트</b>들의 Gemini 구현.
 * Gemini 가 정상이면 그 문구를, 실패·빈 응답·근거 없는 숫자이면 템플릿 문구를 돌려주고 절대 던지지 않는다.
 */
class GeminiTextGeneratorsTest {

    private static final BusinessException UNAVAILABLE = new BusinessException(ErrorCode.AI_UNAVAILABLE);

    @Nested
    @DisplayName("넛지 문구 (NDG-02)")
    class NudgeText_ {

        private final NudgeTextCommand command =
                new NudgeTextCommand("배달", 101_000, 60_000, new BigDecimal("68.33"), 3, 4);

        private GeminiNudgeTextGenerator generator(ScriptedGemini gemini) {
            return new GeminiNudgeTextGenerator(gemini, new TemplateNudgeTextGenerator());
        }

        @Test
        @DisplayName("정상 응답은 그대로 쓴다")
        void usesGeminiText() {
            NudgeText text = generator(new ScriptedGemini().thenReply("""
                    {"analysis":"이번 주 3일 동안 배달을 4번 시켰어요. 평소보다 41,000원 더 썼어요.",
                     "recommendation":"남은 기간 배달 횟수를 미리 정해 봐요."}""")).generate(command);

            assertThat(text.analysis()).contains("41,000원");
            assertThat(text.recommendation()).contains("횟수");
        }

        @Test
        @DisplayName("서버가 준 적 없는 금액이 끼면 템플릿으로 대체한다")
        void hallucinatedNumberFallsBackToTemplate() {
            NudgeText expected = new TemplateNudgeTextGenerator().generate(command);

            NudgeText text = generator(new ScriptedGemini().thenReply("""
                    {"analysis":"평소보다 99,000원이나 더 썼어요.","recommendation":"줄여 봐요."}""")).generate(command);

            assertThat(text.analysis()).doesNotContain("99,000");
            assertThat(text.analysis()).isNotBlank();
            assertThat(expected.analysis()).isNotBlank();
        }

        @Test
        @DisplayName("빈 응답이면 템플릿으로 대체한다")
        void blankFallsBackToTemplate() {
            NudgeText text = generator(new ScriptedGemini().thenReply("""
                    {"analysis":"  ","recommendation":""}""")).generate(command);

            assertThat(text.analysis()).isNotBlank();
            assertThat(text.recommendation()).isNotBlank();
        }

        @Test
        @DisplayName("Gemini 가 실패해도 던지지 않고 템플릿 문구를 준다 (명세 0장 4) 각주)")
        void neverThrows() {
            NudgeText text = generator(new ScriptedGemini().thenThrow(UNAVAILABLE)).generate(command);

            assertThat(text.analysis()).isNotBlank();
            assertThat(text.recommendation()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("코칭 첫 메시지 (COACH-01)")
    class Opening {

        private final CoachingOpeningCommand command =
                CoachingOpeningCommand.fromNudge("홍길동", "배달", 101_000, 60_000);

        private GeminiCoachingOpeningGenerator generator(ScriptedGemini gemini) {
            return new GeminiCoachingOpeningGenerator(gemini, new TemplateCoachingOpeningGenerator());
        }

        @Test
        @DisplayName("정상 응답은 그대로 쓴다")
        void usesGeminiMessage() {
            String message = generator(new ScriptedGemini().thenReply("""
                    {"message":"홍길동님, 이번 주 배달이 101,000원으로 평소(60,000원)보다 늘었어요. 어떤 점이 신경 쓰이세요?"}"""))
                    .generate(command);

            assertThat(message).startsWith("홍길동님");
        }

        @Test
        @DisplayName("근거 없는 숫자가 끼면 템플릿으로 대체한다")
        void hallucinatedNumberFallsBack() {
            String message = generator(new ScriptedGemini().thenReply("""
                    {"message":"홍길동님, 배달이 77,000원이나 늘었어요."}""")).generate(command);

            assertThat(message).doesNotContain("77,000").contains("홍길동님");
        }

        @Test
        @DisplayName("Gemini 가 실패해도 첫 메시지는 항상 나온다 (COACH-01 은 에러를 정의하지 않는다)")
        void neverThrows() {
            assertThat(generator(new ScriptedGemini().thenThrow(UNAVAILABLE)).generate(command))
                    .contains("홍길동님");
            assertThat(generator(new ScriptedGemini().thenThrow(UNAVAILABLE))
                    .generate(CoachingOpeningCommand.withoutNudge("홍길동"))).contains("홍길동님");
        }
    }

    @Nested
    @DisplayName("성장 리포트 피드백 (CHL-03)")
    class Feedback {

        private final GrowthFeedbackCommand command =
                new GrowthFeedbackCommand(true, GoalType.AMOUNT, 30_000, 9_000, 15_000, 7);

        private GeminiGrowthFeedbackGenerator generator(ScriptedGemini gemini) {
            return new GeminiGrowthFeedbackGenerator(gemini, new TemplateGrowthFeedbackGenerator());
        }

        @Test
        @DisplayName("정상 응답은 그대로 쓴다")
        void usesGeminiFeedback() {
            assertThat(generator(new ScriptedGemini().thenReply("""
                    {"feedback":"목표 30,000원 안에서 9,000원만 쓰셨어요. 잘하셨어요!"}""")).generate(command))
                    .contains("9,000원");
        }

        @Test
        @DisplayName("챌린지 카테고리가 프롬프트 입력에 들어간다 — 모델이 엉뚱한 카테고리를 지어내지 않게")
        void categoryIsPartOfTheInput() {
            ScriptedGemini gemini = new ScriptedGemini().thenReply("{\"feedback\":\"배달을 잘 줄였어요.\"}");

            new GeminiGrowthFeedbackGenerator(gemini, new TemplateGrowthFeedbackGenerator())
                    .generate(new GrowthFeedbackCommand(true, GoalType.COUNT, 3, 1, 101_000, 7, "배달"));

            assertThat(gemini.conversations.get(0).get(0).text()).contains("카테고리 배달");
        }

        @Test
        @DisplayName("실제 호출에서 나온 환각: 배달 챌린지인데 '카페'를 말하면 템플릿으로 대체한다")
        void otherCategoryFallsBackToTemplate() {
            GrowthFeedbackCommand deliveryChallenge =
                    new GrowthFeedbackCommand(true, GoalType.COUNT, 3, 1, 101_000, 7, "배달");

            String feedback = new GeminiGrowthFeedbackGenerator(
                    new ScriptedGemini().thenReply("{\"feedback\":\"카페 지출을 훌륭하게 통제하셨네요!\"}"),
                    new TemplateGrowthFeedbackGenerator()).generate(deliveryChallenge);

            assertThat(feedback).doesNotContain("카페").isNotBlank();
            // 같은 카테고리를 말하는 건 문제가 아니다
            assertThat(new GeminiGrowthFeedbackGenerator(
                    new ScriptedGemini().thenReply("{\"feedback\":\"배달 횟수를 잘 줄였어요!\"}"),
                    new TemplateGrowthFeedbackGenerator()).generate(deliveryChallenge)).contains("배달");
        }

        @Test
        @DisplayName("근거 없는 숫자·빈 응답·실패는 모두 템플릿으로 대체한다")
        void fallsBackToTemplate() {
            assertThat(generator(new ScriptedGemini().thenReply("{\"feedback\":\"88,000원 아꼈어요\"}"))
                    .generate(command)).doesNotContain("88,000");
            assertThat(generator(new ScriptedGemini().thenReply("{\"feedback\":\"\"}")).generate(command)).isNotBlank();
            assertThat(generator(new ScriptedGemini().thenThrow(UNAVAILABLE)).generate(command)).isNotBlank();
        }
    }
}
