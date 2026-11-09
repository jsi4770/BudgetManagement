package com.back.was.domain.ai;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 어댑터가 아직 없는 동안의 {@link CoachingReplyGenerator} 구현체 — 항상 던진다.
 *
 * <p>{@code UnavailableTransactionTextParser} 와 같은 성격이지만 <b>사용자에게 보이는
 * 결과는 정반대</b>다. TX-07 은 이 예외가 503 으로 그대로 나가지만, COACH-03 은 서비스가
 * 잡아서 {@code docs/AI_대체_문구.md} 4·5장의 대체 응답으로 바꿔 <b>200</b> 을 준다
 * (서버_내부_처리 8장: "에러 대신 대체 메시지로 200"). 그래서 이 클래스가 있는 상태로도
 * "넛지 → 코칭 → 챌린지 확정" 흐름이 끝까지 돌아간다 — AI_대체_문구 5장이 노리는 바다.
 *
 * <p>즉 지금은 <b>모든</b> COACH-03 응답이 {@code isFallback: true} 다. 가짜 대화를
 * 돌려주지 않는 이유는 TX-07 과 같다: 그럴듯한 응답을 만들면 Gemini 가 붙은 것처럼 보여서
 * 미구현이 숨는다. 대체 문구는 "지금은 AI 코치가 잠시 쉬고 있어요"라고 솔직하게 말한다.
 *
 * <p><b>Spring AI 어댑터를 추가할 때는 이 클래스를 삭제하라.</b> 대체 문구 쪽
 * ({@code CoachingReplyFallback})은 <b>남는다</b> — 그게 명세가 요구하는 실패 경로다.
 */
@Slf4j
@Component
public class UnavailableCoachingReplyGenerator implements CoachingReplyGenerator {

    @Override
    public CoachingReply reply(CoachingReplyCommand command) {
        log.debug("AI 코칭 대화 어댑터가 없어 대체 응답으로 넘긴다 (대화 {}줄)",
                command.conversation().size());
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
}
