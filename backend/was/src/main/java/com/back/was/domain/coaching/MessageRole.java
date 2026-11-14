package com.back.was.domain.coaching;

/**
 * ERD 8장 {@code COACHING_MESSAGE.ROLE} — 발화자.
 *
 * <p>값이 둘뿐이라 {@code boolean} 로도 되지만, 컬럼이 ENUM 이고 응답에도 문자열로 그대로
 * 나가며(명세 COACH-01~03 {@code messages[].role}) Gemini 대화 맥락도 이 구분으로 넘어간다.
 *
 * <p>AI 응답이 대체 문구였는지({@code isFallback})는 <b>여기에 담기지 않는다</b> — ERD 8장이
 * 저장하지 않기로 정했고 응답 전용이다(명세 COACH-03).
 */
public enum MessageRole {

    /** 사용자가 보낸 메시지 (COACH-03). */
    USER,

    /** AI 코치의 메시지. 첫 메시지(COACH-01)도 이 값이다. */
    AI
}
