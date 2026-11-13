package com.back.was.ai.gemini;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Gemini 호출 창구. 파싱·OCR 정리·넛지 문구·코칭은 모두 이 인터페이스만 쓴다.
 * - HttpGeminiClient : 실제 호출 (gemini.mock=false, 기본)
 * - MockGeminiClient : 가짜 응답 (gemini.mock=true)
 *
 * 실패 시: AiUnavailableException(503) / AiParseFailedException(422)
 */
public interface GeminiClient {

    /**
     * 대화 여러 턴 → 지정한 스키마의 JSON.
     *
     * @param systemInstruction 시스템 프롬프트 (역할·규칙·예시)
     * @param messages          user/model 번갈아 가며, 마지막은 user
     * @param responseSchema    응답 JSON 모양 (GeminiSchema 로 생성)
     * @param temperature       0.0(항상 같은 답, 파싱용) ~ 1.0(다양한 답, 코칭 문구용)
     */
    JsonNode generateJson(String systemInstruction, List<GeminiMessage> messages,
                          Map<String, Object> responseSchema, double temperature);

    /** 텍스트 한 번 → JSON (파싱, 넛지 문구 등 대부분 이걸 씀) */
    default JsonNode generateJson(String systemInstruction, String userText,
                                  Map<String, Object> responseSchema, double temperature) {
        return generateJson(systemInstruction, List.of(GeminiMessage.user(userText)), responseSchema, temperature);
    }

    /** 이미지 + 텍스트 → JSON (OCR 서버가 없을 때 Gemini 가 영수증을 직접 읽는 대체 경로) */
    JsonNode generateJson(String systemInstruction, String userText,
                          byte[] imageBytes, String mimeType,
                          Map<String, Object> responseSchema, double temperature);

    /**
     * 도구 호출(function calling) 대화. 모델이 도구를 부르면 handler 로 실행해 결과를 돌려주고,
     * 최종 텍스트 답변이 나올 때까지 반복한다. 숫자는 모델이 계산하지 않고 도구가 준 값만 쓰게 하는 용도.
     */
    default String chatWithTools(String systemInstruction, List<GeminiMessage> messages,
                                 List<GeminiTool> tools, double temperature) {
        throw new UnsupportedOperationException("chatWithTools 미지원");
    }
}
