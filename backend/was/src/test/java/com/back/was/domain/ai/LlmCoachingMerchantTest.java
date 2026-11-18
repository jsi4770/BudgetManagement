package com.back.was.domain.ai;

import com.back.was.domain.ai.AiTestFixtures.ScriptedLlm;
import com.back.was.domain.coaching.MessageRole;
import com.back.was.domain.merchant.MerchantGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 코칭 답변의 지역 가맹점 추천 — AI 가 고른 ID 는 후보 안에 있는 것만 살고, 지출 숫자 인용은 허용된다. */
class LlmCoachingMerchantTest {

    private static final List<CoachingTurn> TALK = List.of(
            new CoachingTurn(MessageRole.AI, "홍길동님, 이번 주 식비가 늘었어요."),
            new CoachingTurn(MessageRole.USER, "요즘 외식을 자주 했어요"));

    private static CoachingReplyCommand command() {
        return new CoachingReplyCommand("홍길동", TALK,
                AiTestFixtures.CATEGORIES.stream().filter(c -> "EXPENSE".equals(c.type())).toList(),
                "천안시",
                List.of(new SpendingFact("식비", 320_000, 18)),
                List.of(new MerchantCandidate(11L, "행복마트", "마트·편의점", "식비"),
                        new MerchantCandidate(12L, "두정식당", "식당·주점", "식비")));
    }

    @Test
    @DisplayName("후보에 없는 ID·중복은 버리고 최대 2개만 남긴다")
    void keepsOnlyKnownIds() {
        CoachingReply reply = new LlmCoachingReplyGenerator(new ScriptedLlm().thenReply("""
                {"message":"동네 마트에서 장을 보면 어떨까요?","proposedGoal":null,
                 "recommendedMerchantIds":[11,999,11,12,13]}"""))
                .reply(command());

        assertThat(reply.recommendedMerchantIds()).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("프롬프트로 준 지출 숫자(320,000원·18회)는 답변에서 인용해도 통과한다")
    void spendingNumbersAreAllowed() {
        CoachingReply reply = new LlmCoachingReplyGenerator(new ScriptedLlm().thenReply("""
                {"message":"최근 30일 식비가 320,000원, 18번이었어요. 천안 행복마트에서 장 보는 건 어때요?",
                 "proposedGoal":null,"recommendedMerchantIds":[11]}"""))
                .reply(command());

        assertThat(reply.message()).contains("320,000원");
        assertThat(reply.recommendedMerchantIds()).containsExactly(11L);
    }

    @Test
    @DisplayName("후보가 없는 명령(지역 미설정)이면 모델이 ID 를 줘도 빈 목록이다")
    void noCandidatesNoRecommendation() {
        CoachingReplyCommand plain = new CoachingReplyCommand("홍길동", TALK,
                AiTestFixtures.CATEGORIES.stream().filter(c -> "EXPENSE".equals(c.type())).toList());
        CoachingReply reply = new LlmCoachingReplyGenerator(new ScriptedLlm().thenReply("""
                {"message":"왜 늘었는지 이야기해 줄래요?","proposedGoal":null,"recommendedMerchantIds":[11]}"""))
                .reply(plain);

        assertThat(reply.recommendedMerchantIds()).isEmpty();
    }

    @Test
    @DisplayName("카테고리 → 가맹점 묶음: 배달은 식당·마트, 월세는 없음, 식비는 식당·마트")
    void categoryToGroups() {
        assertThat(MerchantGroup.forCategoryName("배달")).containsExactly(MerchantGroup.FOOD, MerchantGroup.GROCERY);
        assertThat(MerchantGroup.forCategoryName("식비")).containsExactly(MerchantGroup.FOOD, MerchantGroup.GROCERY);
        assertThat(MerchantGroup.forCategoryName("카페")).containsExactly(MerchantGroup.CAFE);
        assertThat(MerchantGroup.forCategoryName("월세")).isEmpty();
    }
}
