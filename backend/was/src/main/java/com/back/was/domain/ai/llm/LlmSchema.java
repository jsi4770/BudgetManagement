package com.back.was.domain.ai.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 응답 JSON 모양(JSON Schema)을 만드는 도우미. Claude 구조화 출력({@code output_config.format})에 그대로 들어간다.
 * properties 는 LinkedHashMap 으로 넘기면 넣은 순서대로 필드 순서가 고정된다.
 *
 * <p>구조화 출력은 객체마다 {@code additionalProperties: false} 를 요구한다 — {@link #object} 가 붙인다.
 */
public final class LlmSchema {

    private LlmSchema() {}

    public static Map<String, Object> string(String description)  { return leaf("string", description); }
    public static Map<String, Object> integer(String description) { return leaf("integer", description); }
    public static Map<String, Object> number(String description)  { return leaf("number", description); }
    public static Map<String, Object> bool(String description)    { return leaf("boolean", description); }

    public static Map<String, Object> array(Map<String, Object> items, String description) {
        Map<String, Object> m = leaf("array", description);
        m.put("items", items);
        return m;
    }

    /** 객체. required 에 넣은 필드는 응답에 항상 들어온다 (nullable 이면 값은 null 가능) */
    public static Map<String, Object> object(Map<String, Map<String, Object>> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", new LinkedHashMap<>(properties));
        m.put("required", required);
        m.put("additionalProperties", false);
        return m;
    }

    /** 모든 필드를 required 로 */
    public static Map<String, Object> object(Map<String, Map<String, Object>> properties) {
        return object(properties, new ArrayList<>(properties.keySet()));
    }

    /** "못 뽑으면 null" 허용 — {@code anyOf: [schema, {type: null}]} */
    public static Map<String, Object> nullable(Map<String, Object> schema) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anyOf", List.of(schema, Map.of("type", "null")));
        return m;
    }

    private static Map<String, Object> leaf(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (description != null) m.put("description", description);   // 설명도 모델에게 전달됨
        return m;
    }
}
