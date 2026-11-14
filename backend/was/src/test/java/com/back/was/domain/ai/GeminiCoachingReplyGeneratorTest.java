package com.back.was.domain.ai;

import com.back.was.domain.ai.AiTestFixtures.ScriptedGemini;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.coaching.MessageRole;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * COACH-03 대화 응답 — <b>실패를 숨기지 않는 포트</b>. 호출자가 예외를 잡아 {@code isFallback: true} 로 200 을
 * 내리므로, 여기서는 실패를 그대로 던지는지가 계약이다.
 */
class GeminiCoachingReplyGeneratorTest {

    private static final List<CategoryResponse> EXPENSES = AiTestFixtures.CATEGORIES.stream()
            .filter(c -> "EXPENSE".equals(c.type())).toList();

    private static CoachingReplyCommand command(CoachingTurn... turns) {
        return new CoachingReplyCommand("홍길동", List.of(turns), EXPENSES);
    }

    private static final CoachingTurn OPENING =
            new CoachingTurn(MessageRole.AI, "홍길동님, 이번 주 배달이 101,000원으로 평소(60,000원)보다 늘었어요.");
    private static final CoachingTurn USER = new CoachingTurn(MessageRole.USER, "퇴근하고 요리하기 귀찮아서요");

    @Test
    @DisplayName("답변과 제안(AMOUNT)을 ProposedGoal 로 돌려준다")
    void parsesReplyWithProposal() {
        CoachingReply reply = new GeminiCoachingReplyGenerator(new ScriptedGemini().thenReply("""
                {"message":"그렇다면 이번 주 배달을 60,000원 이내로 해보면 어때요?",
                 "proposedGoal":{"categoryId":2,"title":"배달 60,000원 이내","goalType":"AMOUNT",
                                 "goalAmount":60000,"goalCount":null,"periodDays":7}}"""))
                .reply(command(OPENING, USER));

        assertThat(reply.message()).contains("60,000원");
        assertThat(reply.proposal()).isEqualTo(
                new ProposedGoal(2L, "배달 60,000원 이내", GoalType.AMOUNT, 60_000, null, 7));
    }

    @Test
    @DisplayName("COUNT 제안은 goalCount 만 남기고, 허용되지 않는 기간은 7일로 맞춘다")
    void normalizesCountProposal() {
        CoachingReply reply = new GeminiCoachingReplyGenerator(new ScriptedGemini().thenReply("""
                {"message":"이번 주 배달을 2번 이하로 해봐요.",
                 "proposedGoal":{"categoryId":2,"title":"","goalType":"COUNT",
                                 "goalAmount":50000,"goalCount":2,"periodDays":10}}"""))
                .reply(command(OPENING, USER));

        assertThat(reply.proposal()).isEqualTo(new ProposedGoal(2L, null, GoalType.COUNT, null, 2, 7));
    }

    @Test
    @DisplayName("제안이 없으면 null — 메시지만 내려간다")
    void noProposal() {
        CoachingReply reply = new GeminiCoachingReplyGenerator(new ScriptedGemini().thenReply("""
                {"message":"왜 늘었는지 조금 더 이야기해 줄래요?","proposedGoal":null}"""))
                .reply(command(OPENING, USER));

        assertThat(reply.proposal()).isNull();
    }

    @Test
    @DisplayName("월세·없는 카테고리·알 수 없는 유형·목표 값 없음은 제안을 버리고 메시지는 살린다")
    void invalidProposalsAreDropped() {
        String[] goals = {
                "{\"categoryId\":5,\"goalType\":\"AMOUNT\",\"goalAmount\":10000,\"periodDays\":7}",      // 월세
                "{\"categoryId\":999,\"goalType\":\"AMOUNT\",\"goalAmount\":10000,\"periodDays\":7}",    // 없는 카테고리
                "{\"categoryId\":7,\"goalType\":\"AMOUNT\",\"goalAmount\":10000,\"periodDays\":7}",      // 수입 카테고리
                "{\"categoryId\":2,\"goalType\":\"WEIRD\",\"goalAmount\":10000,\"periodDays\":7}",       // 유형
                "{\"categoryId\":2,\"goalType\":\"AMOUNT\",\"goalAmount\":null,\"periodDays\":7}"        // 값 없음
        };
        for (String goal : goals) {
            CoachingReply reply = new GeminiCoachingReplyGenerator(new ScriptedGemini().thenReply(
                    "{\"message\":\"이렇게 해봐요.\",\"proposedGoal\":" + goal + "}")).reply(command(OPENING, USER));

            assertThat(reply.message()).isEqualTo("이렇게 해봐요.");
            assertThat(reply.proposal()).as(goal).isNull();
        }
    }

    @Test
    @DisplayName("근거 없는 숫자가 있으면 한 번 다시 시키고, 고쳐지면 그 답을 쓴다")
    void retriesOnceOnHallucinatedNumber() {
        ScriptedGemini gemini = new ScriptedGemini()
                .thenReply("{\"message\":\"평소보다 99,000원 늘었네요.\",\"proposedGoal\":null}")
                .thenReply("{\"message\":\"평소(60,000원)보다 늘었네요.\",\"proposedGoal\":null}");

        CoachingReply reply = new GeminiCoachingReplyGenerator(gemini).reply(command(OPENING, USER));

        assertThat(reply.message()).contains("60,000원");
        assertThat(gemini.calls()).isEqualTo(2);
        // 두 번째 호출에는 어떤 숫자가 문제였는지가 사용자 턴으로 붙는다
        List<com.back.was.domain.ai.gemini.GeminiMessage> retry = gemini.conversations.get(1);
        assertThat(retry.get(retry.size() - 1).role()).isEqualTo("user");
        assertThat(retry.get(retry.size() - 1).text()).contains("99,000");
    }

    @Test
    @DisplayName("다시 시켜도 근거 없는 숫자가 남으면 422 AI_PARSE_FAILED 를 던진다 (호출자가 대체 응답으로 바꾼다)")
    void throwsWhenStillHallucinating() {
        ScriptedGemini gemini = new ScriptedGemini()
                .thenReply("{\"message\":\"99,000원 늘었어요.\",\"proposedGoal\":null}")
                .thenReply("{\"message\":\"88,000원 늘었어요.\",\"proposedGoal\":null}");

        assertThatThrownBy(() -> new GeminiCoachingReplyGenerator(gemini).reply(command(OPENING, USER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_PARSE_FAILED));
    }

    @Test
    @DisplayName("Gemini 장애는 숨기지 않고 그대로 던진다 (isFallback 의 근거)")
    void propagatesUnavailable() {
        ScriptedGemini gemini = new ScriptedGemini().thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        assertThatThrownBy(() -> new GeminiCoachingReplyGenerator(gemini).reply(command(OPENING, USER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("빈 답변은 422 AI_PARSE_FAILED")
    void blankMessageIsParseFailure() {
        ScriptedGemini gemini = new ScriptedGemini().thenReply("{\"message\":\" \",\"proposedGoal\":null}");

        assertThatThrownBy(() -> new GeminiCoachingReplyGenerator(gemini).reply(command(OPENING, USER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_PARSE_FAILED));
    }

    @Test
    @DisplayName("대화는 user 로 시작하고 같은 역할은 합쳐서 보낸다 — Gemini 는 user/model 교대를 요구한다")
    void conversationIsAlternatingAndStartsWithUser() {
        ScriptedGemini gemini = new ScriptedGemini().thenReply("{\"message\":\"네.\",\"proposedGoal\":null}");

        new GeminiCoachingReplyGenerator(gemini).reply(command(
                OPENING, USER, new CoachingTurn(MessageRole.USER, "그리고 야근도 많아요")));

        var sent = gemini.conversations.get(0);
        assertThat(sent.get(0).role()).isEqualTo("user");                     // 합성 시작 턴
        assertThat(sent).extracting(m -> m.role()).containsExactly("user", "model", "user");
        assertThat(sent.get(2).text()).contains("요리하기 귀찮아서요").contains("야근");
        // 카테고리 목록은 DB 값이고 월세는 빠진다
        assertThat(gemini.systemPrompts.get(0)).contains("id=2 배달").doesNotContain("id=5").doesNotContain("id=7");
    }
}
