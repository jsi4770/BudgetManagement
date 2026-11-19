package com.back.was.domain.ai.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 가짜 LLM. 실제 호출 없이 스키마 모양대로 기본값을 채워 돌려준다.
 * claude.mock=true (환경변수 CLAUDE_MOCK=true) 일 때만 빈으로 등록된다.
 *
 * 쓰는 곳: API 키 없는 팀원의 개발, GitHub Actions 테스트 (키·요금 필요 없음)
 * 실패 흉내: 입력에 "FAIL_422" → 422, "FAIL_503" → 503 (계획표 테스트 항목용)
 */
@Component
@ConditionalOnProperty(prefix = "claude", name = "mock", havingValue = "true")
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);
    private final ObjectMapper objectMapper;
    private final ClaudeProperties props;

    public MockLlmClient(ObjectMapper objectMapper, ClaudeProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
        log.warn("[LLM] MOCK 모드입니다. 실제 AI 를 호출하지 않습니다.");
    }

    @Override
    public JsonNode generateJson(String systemInstruction, List<LlmMessage> messages,
                                 Map<String, Object> responseSchema) {
        delay();
        String last = messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
        if (last != null && last.contains("FAIL_422")) throw LlmErrors.parseFailed("(mock) 파싱 실패");
        if (last != null && last.contains("FAIL_503")) throw LlmErrors.unavailable("(mock) AI 장애");
        return fill(responseSchema);
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userText, byte[] imageBytes, String mimeType,
                                 Map<String, Object> responseSchema) {
        return generateJson(systemInstruction, List.of(LlmMessage.user(userText)), responseSchema);
    }

    /** 성능 테스트용 인위적 지연 (claude.mock-delay-ms) */
    private void delay() {
        if (props.mockDelayMs() > 0) {
            try { Thread.sleep(props.mockDelayMs()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /** 스키마를 따라가며 기본값: 문자열 "mock", 숫자 1000, boolean true, 배열 []. nullable(anyOf)은 null 이 아닌 쪽 */
    @SuppressWarnings("unchecked")
    private JsonNode fill(Map<String, Object> schema) {
        if (schema.get("anyOf") instanceof List<?> branches) {
            return fill((Map<String, Object>) branches.get(0));
        }
        String type = String.valueOf(schema.get("type"));
        switch (type) {
            case "object": {
                ObjectNode node = objectMapper.createObjectNode();
                Map<String, Object> props = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
                props.forEach((k, v) -> node.set(k, fill((Map<String, Object>) v)));
                return node;
            }
            case "array":
                return objectMapper.createArrayNode();
            case "integer":
            case "number":
                return objectMapper.getNodeFactory().numberNode(1000);
            case "boolean":
                return objectMapper.getNodeFactory().booleanNode(true);
            default:
                return objectMapper.getNodeFactory().textNode("mock");
        }
    }
}
