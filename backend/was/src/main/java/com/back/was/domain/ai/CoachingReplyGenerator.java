package com.back.was.domain.ai;

import com.back.was.global.exception.BusinessException;

/**
 * COACH-03 대화 응답 생성 포트 (서버_내부_처리 8장).
 *
 * <p><b>이 포트는 실패를 숨기지 않는다 — 다른 두 코칭/넛지 포트와 정반대다.</b>
 * {@link NudgeTextGenerator} 와 {@link CoachingOpeningGenerator} 는 안에서 템플릿으로
 * 대체하고 호출자에게는 성공만 돌려주지만, 이쪽은 예외를 던진다. 이유가 둘 있다.
 *
 * <ol>
 *   <li><b>{@code isFallback} 이 응답 계약이다</b>(명세 COACH-03). 프론트가 "AI 연결이
 *       원활하지 않아요"를 띄워야 하므로 실패 사실이 바깥으로 나와야 한다.</li>
 *   <li><b>대체 경로가 DB 를 본다.</b> {@code docs/AI_대체_문구.md} 5장의 규칙 기반 제안은
 *       직전 4주 지출·예산의 소비 관리 목표·이번 주 넛지를 읽어야 한다. AI 어댑터는 DB 를
 *       몰라야 하므로(이 패키지의 공통 규칙) 대체 경로가 포트 안에 있을 수 없다.</li>
 * </ol>
 *
 * <p>그래서 <b>호출자에게 {@code try/catch} 가 있다</b>. 대체 응답을 만드는 것은
 * {@code CoachingReplyFallback} 이고, 서비스는 그 결과에 {@code isFallback: true} 를
 * 붙여 <b>200 으로</b> 내려보낸다 — 명세 0장 4) 각주·8장이 코칭 대화는 에러를 내지 말라고
 * 정했기 때문이다. <b>이 포트의 예외가 사용자에게 503 으로 나가는 일은 없어야 한다.</b>
 *
 * <p>계약은 TX-07 의 {@code TransactionTextParser} 와 같다: 타임아웃 10초, 호출 실패·한도
 * 초과·타임아웃 → {@code AI_UNAVAILABLE}, 구조화 출력을 읽지 못하면
 * {@code AI_PARSE_FAILED}, API 키는 환경 변수에서만. 프롬프트와 JSON 스키마는 AI 담당
 * 몫이다(8장).
 *
 * <p>현재 유일한 구현은 {@link UnavailableCoachingReplyGenerator} 이고 항상 던진다 —
 * 즉 <b>지금 COACH-03 은 언제나 대체 응답으로 동작한다</b>. 실제 어댑터가 오면 그 클래스는
 * <b>삭제</b>한다.
 */
public interface CoachingReplyGenerator {

    /**
     * @return 문장과 (제안했다면) 목표 제안
     * @throws BusinessException {@code AI_UNAVAILABLE} / {@code AI_PARSE_FAILED} —
     *                           <b>서비스가 잡아서 대체 응답으로 바꾼다.</b> 사용자에게
     *                           그대로 나가면 명세 위반이다
     */
    CoachingReply reply(CoachingReplyCommand command);
}
