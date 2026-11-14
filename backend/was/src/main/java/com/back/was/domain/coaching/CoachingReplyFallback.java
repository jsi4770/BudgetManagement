package com.back.was.domain.coaching;

import com.back.was.domain.ai.CoachingFallbackMessage;
import com.back.was.domain.ai.CoachingReply;
import com.back.was.domain.ai.CoachingReplyGenerator;
import com.back.was.domain.ai.ProposedGoal;
import com.back.was.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gemini 가 실패했을 때 대신 내려보낼 응답을 만든다 — {@code docs/AI_대체_문구.md} 4·5장을
 * 하나로 묶는 자리다 (서버_내부_처리 8장: "에러 대신 대체 메시지로 200, 제안이 필요한
 * 시점이면 규칙 기반 제안 포함").
 *
 * <p>왜 {@link CoachingReplyGenerator} 구현체가 아니라 별도 컴포넌트인가: 규칙 기반 제안이
 * <b>DB 를 읽어야</b> 하고({@link RuleBasedGoalProposer}) AI 패키지는 DB 를 모르기
 * 때문이다. 그래서 넛지 문구·코칭 첫 메시지와 달리 대체 경로가 포트 <b>밖</b>에 있다.
 * 자세한 비교는 {@code CoachingReplyGenerator} 의 설명에 있다.
 *
 * <p>돌려주는 타입은 포트와 <b>같은</b> {@link CoachingReply} 다 — 서비스가 두 경로를
 * 구분 없이 저장할 수 있고, 다른 점은 서비스가 붙이는 {@code isFallback} 플래그뿐이다.
 *
 * <p><b>Gemini 어댑터가 와도 이 클래스는 남는다.</b> 그게 명세가 요구하는 실패 경로다
 * ({@code UnavailableCoachingReplyGenerator} 는 삭제 대상, 이쪽은 유지 — 넛지의
 * {@code TemplateNudgeTextGenerator} 와 같은 성격).
 */
@Component
@RequiredArgsConstructor
public class CoachingReplyFallback {

    private final RuleBasedGoalProposer ruleBasedGoalProposer;

    /**
     * <p>제안을 만들 수 있으면 4장 <b>3번</b> 문구("아래 추천 목표로 바로 시작해 볼 수도
     * 있어요")와 함께, 못 만들면 4장 1·2번 문구만 보낸다 — 문서가 3번에 달아 둔 조건이
     * 그것이고, 가리킬 카드가 없는데 3번을 보내면 사용자가 없는 버튼을 찾는다.
     *
     * <p><b>항상 제안을 시도한다.</b> "제안이 필요한 시점"을 판단할 대화 이해가 서버에는
     * 없고(그 판단이 Gemini 의 몫이다), 명세 COACH-03 이 "재제안이 필요하면 메시지 재전송"
     * 이라고 적은 것과도 맞는다 — 메시지를 다시 보내면 제안이 다시 온다.
     */
    public CoachingReply of(User user) {
        ProposedGoal proposal = ruleBasedGoalProposer.propose(user).orElse(null);
        String message = proposal == null
                ? CoachingFallbackMessage.withoutProposal()
                : CoachingFallbackMessage.withProposal();

        return new CoachingReply(message, proposal);
    }
}
