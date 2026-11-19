package com.back.was.domain.merchant;

import com.back.was.domain.ai.MerchantCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 사용자의 말이 "우리 동네 가맹점을 묻는 것"인지 — 묻는 말에서만 가맹점 후보가 만들어진다. */
class MerchantIntentTest {

    private static Optional<MerchantIntent> detect(String message) {
        return MerchantIntent.detect(message);
    }

    @Test
    @DisplayName("가맹점·지역화폐를 직접 말하면 질문이고, 업종 말이 있으면 그 업종을 읽는다")
    void directQuestion() {
        assertThat(detect("카페 가맹점 알려줘")).hasValueSatisfying(i -> assertThat(i.groups()).containsExactly(MerchantGroup.CAFE));
        assertThat(detect("카페랑 식당 가맹점이 궁금해")).hasValueSatisfying(i ->
                assertThat(i.groups()).containsExactlyInAnyOrder(MerchantGroup.CAFE, MerchantGroup.FOOD));
        assertThat(detect("우리 동네 지역화폐 쓸 수 있는 곳 알려줘")).hasValueSatisfying(i -> assertThat(i.groups()).isEmpty());
        assertThat(detect("지역 화폐 사용처가 어디야?")).isPresent();
    }

    @Test
    @DisplayName("업종 말만 있어도 어디·추천·근처 같은 물음이 함께 있으면 질문이다")
    void groupWithAsk() {
        assertThat(detect("근처 마트 어디야")).hasValueSatisfying(i -> assertThat(i.groups()).containsExactly(MerchantGroup.GROCERY));
        assertThat(detect("점심 먹을 식당 추천해줘")).hasValueSatisfying(i -> assertThat(i.groups()).containsExactly(MerchantGroup.FOOD));
        assertThat(detect("약국 추천해줘")).hasValueSatisfying(i -> assertThat(i.groups()).containsExactly(MerchantGroup.HEALTH));
    }

    @Test
    @DisplayName("소비 이야기는 질문이 아니다 — 업종 말이 있어도 가맹점을 묻는 게 아니면 후보를 만들지 않는다")
    void spendingTalkIsNotAQuestion() {
        assertThat(detect("이번 주에 카페 지출이 늘었어요")).isEmpty();
        assertThat(detect("카페 지출 얼마나 썼는지 알려줘")).isEmpty();
        assertThat(detect("요즘 배달을 자주 시켜요")).isEmpty();
        assertThat(detect("배달 줄일 방법 알려줘")).isEmpty();
        assertThat(detect("퇴근하면 힘들어서 자꾸 시키게 돼요")).isEmpty();
        assertThat(detect("왜 이렇게 많이 썼는지 모르겠어요")).isEmpty();
    }

    @Test
    @DisplayName("빈 말·null 은 질문이 아니다")
    void blank() {
        assertThat(detect(null)).isEmpty();
        assertThat(detect("   ")).isEmpty();
    }

    @Test
    @DisplayName("AI 가 못 골랐을 때의 대체 추천: 업종마다 하나씩 먼저, 최대 2곳, 순서 유지")
    void fallbackPicks() {
        MerchantRecommender recommender = new MerchantRecommender(null);
        List<MerchantCandidate> candidates = List.of(
                new MerchantCandidate(1L, "가식당", "식당·주점", "식비"),
                new MerchantCandidate(2L, "나식당", "식당·주점", "식비"),
                new MerchantCandidate(3L, "다카페", "카페", "카페"));

        assertThat(recommender.fallbackPicks(candidates)).containsExactly(1L, 3L);
        assertThat(recommender.fallbackPicks(candidates.subList(0, 2))).containsExactly(1L, 2L);
        assertThat(recommender.fallbackPicks(List.of())).isEmpty();
    }
}
