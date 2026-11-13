package com.back.was.ai.gemini;

/**
 * 대화 한 턴. role 은 "user"(사용자) 또는 "model"(AI).
 * 코칭처럼 여러 턴을 이어서 보낼 때 사용.
 */
public record GeminiMessage(String role, String text) {

    public static GeminiMessage user(String text) {
        return new GeminiMessage("user", text);
    }

    public static GeminiMessage model(String text) {
        return new GeminiMessage("model", text);
    }
}
