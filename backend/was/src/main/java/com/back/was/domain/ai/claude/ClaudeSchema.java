package com.back.was.domain.ai.claude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GeminiSchema} 로 만든 스키마를 Claude 구조화 출력이 받는 표준 JSON Schema 로 바꾼다.
 *
 * <p>호출하는 쪽(넛지·코칭·TX-07·영수증)은 그대로 {@code GeminiSchema} 를 쓰고, 차이는 여기서만 흡수한다:
 * <ul>
 *   <li>타입 이름: {@code STRING} → {@code string} (Gemini 는 대문자)</li>
 *   <li>{@code nullable: true} → {@code anyOf: [원래 타입, null]} (표준 JSON Schema 에는 nullable 이 없다)</li>
 *   <li>객체에 {@code additionalProperties: false} — Claude 구조화 출력은 모든 객체에 이 값을 요구한다</li>
 *   <li>{@code propertyOrdering} 제거 — Gemini 전용 키라 그대로 보내면 400</li>
 * </ul>
 */
final class ClaudeSchema {

    private ClaudeSchema() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toJsonSchema(Map<String, Object> gemini) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object description = gemini.get("description");
        String type = String.valueOf(gemini.get("type")).toLowerCase();

        out.put("type", type);
        if (gemini.containsKey("enum")) out.put("enum", gemini.get("enum"));

        if (type.equals("object")) {
            Map<String, Object> properties = new LinkedHashMap<>();
            ((Map<String, Map<String, Object>>) gemini.getOrDefault("properties", Map.of()))
                    .forEach((name, child) -> properties.put(name, toJsonSchema(child)));
            out.put("properties", properties);
            out.put("required", new ArrayList<>((List<String>) gemini.getOrDefault("required", List.of())));
            out.put("additionalProperties", false);
        } else if (type.equals("array")) {
            out.put("items", toJsonSchema((Map<String, Object>) gemini.get("items")));
        }

        if (Boolean.TRUE.equals(gemini.get("nullable"))) {
            Map<String, Object> nullable = new LinkedHashMap<>();
            if (description != null) nullable.put("description", description);
            nullable.put("anyOf", List.of(out, Map.of("type", "null")));
            return nullable;
        }
        if (description != null) out.put("description", description);
        return out;
    }
}
