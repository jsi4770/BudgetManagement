package com.back.was.transaction.parse;

import com.back.was.ai.gemini.GeminiSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gemini 가 채워야 하는 JSON 모양 (한 줄 파싱·영수증 공용).
 * correctedText / amountReasoning 은 디버깅 로그용이고 응답에는 안 나간다.
 */
public final class ParsedTxSchema {

    private ParsedTxSchema() {}

    public static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> p = new LinkedHashMap<>();
        p.put("isTransaction", GeminiSchema.bool("지출·수입 정보가 있으면 true, 잡담·무의미한 입력이면 false"));
        p.put("correctedText", GeminiSchema.nullable(GeminiSchema.string("오타를 바로잡아 다시 쓴 입력")));
        p.put("amount", GeminiSchema.nullable(GeminiSchema.integer("원 단위 정수. 알 수 없으면 null")));
        p.put("amountReasoning", GeminiSchema.nullable(GeminiSchema.string("금액을 어떻게 해석했는지 한 줄")));
        p.put("transactionDate", GeminiSchema.nullable(GeminiSchema.string("YYYY-MM-DD")));
        p.put("categoryId", GeminiSchema.nullable(GeminiSchema.integer("카테고리 목록 중 하나의 id")));
        p.put("memo", GeminiSchema.nullable(GeminiSchema.string("오타를 고친 가게명·품목. 금액·날짜 표현 제외")));
        SCHEMA = GeminiSchema.object(p);
    }
}
