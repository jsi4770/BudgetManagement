package com.back.was.coach;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

final class TestSupport {
    static final ObjectMapper OM = JsonMapper.builder().build();

    private TestSupport() {}

    static DemoData demo() {
        try { return new DemoData(OM); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static JsonNode golden(String name) {
        try (InputStream in = TestSupport.class.getResourceAsStream("/golden/" + name)) {
            return OM.readTree(in);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** 숫자는 오차 1e-9 이내, 객체는 키 순서 무관, 배열은 순서까지 비교. 다르면 경로를 돌려준다 (같으면 null) */
    static String diff(JsonNode expected, JsonNode actual, String path) {
        if (expected.isNumber() && actual.isNumber()) {
            return Math.abs(expected.asDouble() - actual.asDouble()) < 1e-9 ? null : path + ": " + expected + " != " + actual;
        }
        if (expected.isObject() && actual.isObject()) {
            for (var e : expected.properties()) {
                JsonNode a = actual.get(e.getKey());
                if (a == null) return path + "." + e.getKey() + ": 없음 (기대 " + e.getValue() + ")";
                String d = diff(e.getValue(), a, path + "." + e.getKey());
                if (d != null) return d;
            }
            for (var e : actual.properties()) {
                if (!expected.has(e.getKey())) return path + "." + e.getKey() + ": 기대에 없는 키";
            }
            return null;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) return path + ": 길이 " + expected.size() + " != " + actual.size();
            for (int i = 0; i < expected.size(); i++) {
                String d = diff(expected.get(i), actual.get(i), path + "[" + i + "]");
                if (d != null) return d;
            }
            return null;
        }
        return expected.equals(actual) ? null : path + ": " + expected + " != " + actual;
    }
}
