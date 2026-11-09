package com.back.was.domain.ai;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * COACH-03 응답 실패 시의 문구 — {@code docs/AI_대체_문구.md} <b>4장</b>.
 *
 * <p>문서가 세 문장을 주면서 조건을 하나 달았다: <b>3번은 5장의 규칙 기반 목표 제안과 함께
 * 내려보낼 때 쓴다</b>("아래 추천 목표로 바로 시작해 볼 수도 있어요" — 제안이 없는데 이
 * 문장을 보내면 가리킬 카드가 없다). 그래서 메서드가 둘이고, 제안을 함께 보내는지가
 * 호출자의 선택이 아니라 <b>문구 선택의 기준</b>이다.
 *
 * <p>제안이 없을 때만 1·2번 중 <b>무작위로</b> 고른다(문서 머리말: 매번 같은 문장이면
 * 사용자가 고장으로 느낀다). 제안이 있을 때는 고를 여지가 없다 — 3번뿐이다.
 *
 * <p>변수가 없어서 치환이 필요 없고, 그래서 {@code Component} 가 아니라 정적 상수 모음이다
 * ({@code NudgeTitle} 과 같은 모양).
 */
public final class CoachingFallbackMessage {

    /** 4장 1·2번 — 제안 없이 보낼 때. */
    private static final List<String> WITHOUT_PROPOSAL = List.of(
            "지금은 AI 코치가 잠시 쉬고 있어요. 잠시 후에 다시 말을 걸어 주세요.",
            "답변을 준비하는 데 문제가 생겼어요. 조금 뒤에 다시 시도해 주세요."
    );

    /** 4장 3번 — 규칙 기반 제안을 함께 보낼 때만. */
    private static final String WITH_PROPOSAL =
            "지금은 대화가 원활하지 않아요. 아래 추천 목표로 바로 시작해 볼 수도 있어요.";

    private CoachingFallbackMessage() {
    }

    /** 규칙 기반 제안을 함께 내려보낼 때 (4장 3번). */
    public static String withProposal() {
        return WITH_PROPOSAL;
    }

    /** 제안 없이 내려보낼 때 (4장 1·2번 중 무작위). */
    public static String withoutProposal() {
        return WITHOUT_PROPOSAL.get(ThreadLocalRandom.current().nextInt(WITHOUT_PROPOSAL.size()));
    }
}
