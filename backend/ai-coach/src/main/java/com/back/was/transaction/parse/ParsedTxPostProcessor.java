package com.back.was.transaction.parse;

import com.back.was.category.CategoryDto;
import com.back.was.category.CategoryInfo;
import com.back.was.category.CategoryProvider;
import com.back.was.common.AiParseFailedException;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini 결과 → 명세 응답(ParseResult). 모델 출력을 그대로 믿지 않고 서버 규칙으로 한 번 더 검증한다.
 * - 금액: 1 이상 정수만 (TX-01 규칙)
 * - 날짜: 형식만 검증. 미래 날짜는 그대로 (저장 시 TX-01 이 FUTURE_DATE 로 거부)
 * - 카테고리: 목록에 없는 id·null → "기타"
 * - missingFields: 모델이 아니라 서버가 null 을 보고 채움 (SRS 7.1-3)
 */
@Component
public class ParsedTxPostProcessor {

    private static final int MEMO_MAX = 255;

    private final CategoryProvider categories;

    public ParsedTxPostProcessor(CategoryProvider categories) {
        this.categories = categories;
    }

    /**
     * @param baseDate  작성일 (날짜를 못 읽었을 때 채울 값)
     * @param fillToday true: 날짜 없으면 작성일로 (한 줄 입력) / false: null 유지 (영수증)
     */
    public ParseResult process(JsonNode r, LocalDate baseDate, boolean fillToday) {
        if (r == null || !r.path("isTransaction").asBoolean(false)) {
            throw new AiParseFailedException("입력에서 지출 정보를 찾지 못했어요. 직접 입력해 주세요.");
        }

        // 금액
        Long amount = null;
        JsonNode a = r.path("amount");
        if (a.isIntegralNumber() && a.asLong() >= 1) {
            amount = a.asLong();
        }

        // 날짜
        String date = null;
        String rawDate = r.path("transactionDate").asString(null);
        if (rawDate != null && !rawDate.isBlank() && !"null".equals(rawDate)) {
            try {
                date = LocalDate.parse(rawDate.trim()).toString();
            } catch (DateTimeParseException ignored) {
                date = null;
            }
        }
        if (date == null && fillToday) {
            date = baseDate.toString();
        }

        // 카테고리
        JsonNode c = r.path("categoryId");
        CategoryInfo category = c.isIntegralNumber()
                ? categories.findById(c.asLong()).orElse(categories.etc())
                : categories.etc();

        // 메모
        String memo = r.path("memo").isString() ? r.path("memo").asString().strip() : "";
        if (memo.length() > MEMO_MAX) memo = memo.substring(0, MEMO_MAX);
        if (memo.isEmpty()) memo = null;

        List<String> missing = new ArrayList<>();
        if (amount == null) missing.add("amount");
        if (date == null) missing.add("transactionDate");

        return new ParseResult(amount, date, CategoryDto.of(category), memo, missing);
    }
}
