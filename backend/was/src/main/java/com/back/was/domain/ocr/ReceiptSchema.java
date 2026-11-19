package com.back.was.domain.ocr;

import com.back.was.domain.ai.llm.LlmSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Claude 가 영수증·금융 앱 캡처를 직접 읽을 때 채우는 JSON 모양. 한 장에 거래가 여러 건일 수 있어서
 * {@code items} 배열이다 — Django OCR 서버({@code data.items})와 같은 모양. 거래가 없으면 빈 배열.
 */
final class ReceiptSchema {

    private ReceiptSchema() {}

    static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> item = new LinkedHashMap<>();
        item.put("amount", LlmSchema.nullable(LlmSchema.integer("원 단위 양의 정수. 알 수 없으면 null")));
        item.put("transactionDate", LlmSchema.nullable(LlmSchema.string("YYYY-MM-DD. 알 수 없으면 null")));
        item.put("categoryId", LlmSchema.nullable(LlmSchema.integer("카테고리 목록 중 하나의 id")));
        item.put("memo", LlmSchema.nullable(LlmSchema.string("가맹점·거래처 요약")));

        Map<String, Map<String, Object>> root = new LinkedHashMap<>();
        root.put("items", LlmSchema.array(LlmSchema.object(item), "이미지 속 거래들. 위에서 아래 순서. 없으면 빈 배열"));
        SCHEMA = LlmSchema.object(root);
    }
}
