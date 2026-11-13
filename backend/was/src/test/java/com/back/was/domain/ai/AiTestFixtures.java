package com.back.was.domain.ai;

import com.back.was.domain.ai.gemini.GeminiClient;
import com.back.was.domain.ai.gemini.GeminiMessage;
import com.back.was.domain.category.dto.CategoryResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** AI 어댑터 단위 테스트 공용 — DB·네트워크 없이 시드와 같은 카테고리 목록과 가짜 Gemini 를 만든다. */
public final class AiTestFixtures {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** {@code CategorySeeder} 의 시드와 같은 순서·id. "기타"가 지출(6)·수입(9) 양쪽에 있다는 점이 핵심이다. */
    public static final List<CategoryResponse> CATEGORIES = List.of(
            new CategoryResponse(1L, "식비", "EXPENSE"),
            new CategoryResponse(2L, "배달", "EXPENSE"),
            new CategoryResponse(3L, "카페", "EXPENSE"),
            new CategoryResponse(4L, "교통", "EXPENSE"),
            new CategoryResponse(5L, "월세", "EXPENSE"),
            new CategoryResponse(6L, "기타", "EXPENSE"),
            new CategoryResponse(7L, "급여", "INCOME"),
            new CategoryResponse(8L, "용돈", "INCOME"),
            new CategoryResponse(9L, "기타", "INCOME"));

    private AiTestFixtures() {
    }

    public static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    /** 정해 둔 JSON 을 돌려주고, 받은 프롬프트·입력·이미지를 기록하는 가짜 Gemini. */
    public static final class FakeGemini implements GeminiClient {

        private final JsonNode reply;
        public final List<String> systemPrompts = new ArrayList<>();
        public final List<String> userTexts = new ArrayList<>();
        public byte[] lastImage;
        public String lastMime;
        public int calls;

        public FakeGemini(String replyJson) {
            this.reply = json(replyJson);
        }

        @Override
        public JsonNode generateJson(String systemInstruction, List<GeminiMessage> messages,
                                     Map<String, Object> responseSchema, double temperature) {
            calls++;
            systemPrompts.add(systemInstruction);
            userTexts.add(messages.get(messages.size() - 1).text());
            return reply;
        }

        @Override
        public JsonNode generateJson(String systemInstruction, String userText, byte[] imageBytes,
                                     String mimeType, Map<String, Object> responseSchema, double temperature) {
            calls++;
            systemPrompts.add(systemInstruction);
            userTexts.add(userText);
            lastImage = imageBytes;
            lastMime = mimeType;
            return reply;
        }
    }
}
