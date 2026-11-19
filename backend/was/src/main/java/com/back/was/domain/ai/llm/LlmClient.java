package com.back.was.domain.ai.llm;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * LLM 호출 창구. 파싱·영수증·넛지 문구·코칭은 모두 이 인터페이스만 쓴다.
 * - ClaudeLlmClient : 실제 호출 (claude.mock=false, 기본)
 * - MockLlmClient   : 가짜 응답 (claude.mock=true)
 *
 * 실패 시: BusinessException(AI_UNAVAILABLE, 503) / BusinessException(AI_PARSE_FAILED, 422)
 * — {@link LlmErrors} 가 만든다.
 *
 * <p>temperature 는 없다 — Claude 최신 모델은 샘플링 파라미터를 받지 않는다(400). 답의 깊이는 설정의
 * {@code claude.effort} 로 조절한다.
 */
public interface LlmClient {

    /**
     * 대화 여러 턴 → 지정한 스키마의 JSON.
     *
     * @param systemInstruction 시스템 프롬프트 (역할·규칙·예시)
     * @param messages          user/assistant 번갈아 가며, 마지막은 user
     * @param responseSchema    응답 JSON 모양 (LlmSchema 로 생성)
     */
    JsonNode generateJson(String systemInstruction, List<LlmMessage> messages, Map<String, Object> responseSchema);

    /** 텍스트 한 번 → JSON (파싱, 넛지 문구 등 대부분 이걸 씀) */
    default JsonNode generateJson(String systemInstruction, String userText, Map<String, Object> responseSchema) {
        return generateJson(systemInstruction, List.of(LlmMessage.user(userText)), responseSchema);
    }

    /** 이미지 + 텍스트 → JSON (OCR 서버가 없을 때 AI 가 영수증을 직접 읽는 대체 경로) */
    JsonNode generateJson(String systemInstruction, String userText,
                          byte[] imageBytes, String mimeType,
                          Map<String, Object> responseSchema);
}
