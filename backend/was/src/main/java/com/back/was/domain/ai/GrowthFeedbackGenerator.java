package com.back.was.domain.ai;

/**
 * 성장 리포트 피드백 생성 포트 ({@code GROWTH_REPORT.FEEDBACK} — ERD 10장 ·
 * 서버_내부_처리 4장).
 *
 * <p><b>이 포트는 실패하지 않는다</b> — {@link NudgeTextGenerator} ·
 * {@link CoachingOpeningGenerator} 와 같은 계약이다. ERD 10장이 "생성 실패 시 결과별 템플릿
 * 문구"로 정했으므로 Gemini 가 죽어도 호출자는 문장을 받고, 대체 문구로 넘어가는 책임은
 * <b>구현체 안</b>에 있다. 그래서 호출하는 쪽에 {@code try/catch} 가 없다.
 *
 * <p>COACH-03 의 {@code CoachingReplyGenerator} 와 대조적이다. 저쪽은 실패를
 * {@code isFallback} 으로 드러내야 하고 대체 경로가 DB 를 읽지만, 리포트 피드백은 알릴
 * 상대가 없고({@code GROWTH_REPORT} 에 그런 컬럼이 없다) 템플릿에 필요한 값이 모두 인자로
 * 들어온다.
 *
 * <p>구현은 {@link GeminiGrowthFeedbackGenerator}({@code @Primary})이고, 실패하면
 * {@link TemplateGrowthFeedbackGenerator} 에 위임한다. <b>템플릿 쪽을 지우지 말 것</b> —
 * 그게 "실패 시 템플릿"의 구현이다.
 */
public interface GrowthFeedbackGenerator {

    /** @return 항상 채워진 피드백 한 문장 */
    String generate(GrowthFeedbackCommand command);
}
