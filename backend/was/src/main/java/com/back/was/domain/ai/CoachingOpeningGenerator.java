package com.back.was.domain.ai;

/**
 * 코칭 첫 메시지 생성 포트 (COACH-01 / 서버_내부_처리 8장).
 *
 * <p><b>이 포트는 실패하지 않는다</b> — {@link NudgeTextGenerator} 와 같은 계약이다. 명세
 * COACH-01 은 에러를 정의하지 않고 201 과 함께 메시지 하나를 요구하며,
 * {@code docs/AI_대체_문구.md} 3장이 그때 쓸 문구를 미리 정해 두었다. 그래서 Gemini 가
 * 죽어도 호출자는 문장을 받고, 대체 문구로 넘어가는 책임은 <b>구현체 안</b>에 있다.
 *
 * <p>COACH-03 의 대화 응답은 <b>이 포트가 아니다.</b> 그쪽은 실패를 숨기지 않고
 * {@code isFallback: true} 로 프론트에 알려야 하고(명세 COACH-03), 대화 맥락과 구조화
 * 출력(제안)까지 다루므로 계약 자체가 다르다. 첫 메시지는 알릴 상대가 없다 — 사용자는
 * 아직 아무 말도 하지 않았고, "AI 연결이 원활하지 않아요" 안내를 띄울 자리도 없다.
 *
 * <p>현재 유일한 구현은 {@link TemplateCoachingOpeningGenerator} 다. <b>Gemini 어댑터가
 * 들어올 때도 지우지 말고</b> 실패 시 위임하게 해야 한다 — TX-07 의
 * {@code UnavailableTransactionTextParser}("실제 어댑터가 오면 삭제")와 반대 성격이다.
 */
public interface CoachingOpeningGenerator {

    /** @return 항상 채워진 첫 메시지 한 문장 */
    String generate(CoachingOpeningCommand command);
}
