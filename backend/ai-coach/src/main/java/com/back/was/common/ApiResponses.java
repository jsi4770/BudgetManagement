package com.back.was.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 명세 응답 형식 { "success": ..., "data" | "error": ... } 을 만드는 도우미.
 * 팀에 공통 응답 클래스(ApiResponse 등)가 이미 있으면 그걸로 바꿔 써도 됩니다.
 */
public final class ApiResponses {

    private ApiResponses() {}

    /** 성공. data 가 null 이어도 됨 (NDG-01 "data: null" 같은 경우) */
    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    public static Map<String, Object> fail(String code, String message, List<Map<String, String>> details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null && !details.isEmpty()) {
            error.put("details", details);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return body;
    }
}
