package com.back.was.domain.ai;

/**
 * 넛지 문구 생성 포트 (서버_내부_처리 8장).
 *
 * <p><b>이 포트는 실패하지 않는다.</b> 명세 0장 4) 각주와 8장 실패 표가 넛지 문구는
 * "템플릿 문구로 채움"이라고 정했으므로, Gemini 가 죽든 한도를 넘든 호출자는 항상 문구를
 * 받는다. 그래서 호출하는 쪽에 {@code try/catch} 가 없다 — 대체 문구로 넘어가는 책임은
 * <b>구현체 안</b>에 있다.
 *
 * <p>{@code TransactionTextParser}(TX-07)와 대조적인데, 그쪽은 실패를 503 으로 드러내야
 * 한다(사용자가 입력한 문장을 해석하지 못했으면 저장할 값이 없다). 넛지 문구는 없어도
 * 카드가 성립하므로 에러를 낼 이유가 없다.
 *
 * <p>현재 유일한 구현은 {@link TemplateNudgeTextGenerator} 다 —
 * {@code docs/AI_대체_문구.md} 1·2장 템플릿만 쓴다. <b>Gemini 어댑터가 들어올 때도 이
 * 클래스를 지우지 말고</b>, 새 어댑터가 실패 시 이 구현에 위임하게 해야 한다(그게 "실패 시
 * 템플릿"의 구현이다). 프롬프트와 스키마는 AI 담당 몫이다(8장).
 */
public interface NudgeTextGenerator {

    /** @return 항상 두 문구가 채워진 결과 */
    NudgeText generate(NudgeTextCommand command);
}
