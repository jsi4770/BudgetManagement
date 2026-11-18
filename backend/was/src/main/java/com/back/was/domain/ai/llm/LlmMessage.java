package com.back.was.domain.ai.llm;

/**
 * 대화 한 턴. role 은 "user"(사용자) 또는 "assistant"(AI).
 * 코칭처럼 여러 턴을 이어서 보낼 때 사용.
 */
public record LlmMessage(String role, String text) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public static LlmMessage user(String text) {
        return new LlmMessage(USER, text);
    }

    public static LlmMessage assistant(String text) {
        return new LlmMessage(ASSISTANT, text);
    }
}
