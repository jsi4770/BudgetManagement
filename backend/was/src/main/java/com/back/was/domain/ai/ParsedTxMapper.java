package com.back.was.domain.ai;

import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Gemini 의 JSON 결과를 {@link ParsedTransaction} 으로 정규화한다.
 *
 * <p>모델 출력을 그대로 믿지 않고 서버 규칙으로 한 번 더 걸러낸다. 팀 포트 계약
 * ({@link TransactionTextParser})대로 <b>뽑지 못한 필드는 {@code null}</b> 이고,
 * {@code missingFields} 는 서비스가 그 {@code null} 로부터 만든다.
 *
 * <ul>
 *   <li>금액: 1 이상 정수만 (TX-01 규칙). 0·음수·소수·범위 밖은 {@code null}</li>
 *   <li>날짜: 형식만 검증. <b>미래 날짜도 그대로 둔다</b> — 거부는 저장(TX-01)의 몫</li>
 *   <li>카테고리: 목록에 없는 id·null 이면 지출 "기타" (서버_내부_처리 8장 "해당 없으면 기타")</li>
 *   <li>메모: 255자로 자르고, 비면 {@code null}</li>
 * </ul>
 */
public final class ParsedTxMapper {

    private static final int MEMO_MAX = 255;
    private static final String ETC_NAME = "기타";

    private ParsedTxMapper() {
    }

    /**
     * @param fillToday {@code true}: 날짜를 못 읽으면 작성일로 채움(한 줄 입력) /
     *                  {@code false}: {@code null} 유지(영수증 — 오늘로 채우면 오히려 틀린다)
     * @throws BusinessException 422 {@code AI_PARSE_FAILED} — 거래 정보가 아니라고 모델이 판단
     */
    public static ParsedTransaction toParsed(JsonNode r, LocalDate base, boolean fillToday,
                                             List<CategoryResponse> categories) {
        if (r == null || !r.path("isTransaction").asBoolean(false)) {
            throw new BusinessException(ErrorCode.AI_PARSE_FAILED);
        }

        Integer amount = null;
        JsonNode a = r.path("amount");
        if (a.isIntegralNumber() && a.asLong() >= 1 && a.asLong() <= Integer.MAX_VALUE) {
            amount = (int) a.asLong();
        }

        LocalDate date = null;
        String rawDate = r.path("transactionDate").asString(null);
        if (rawDate != null && !rawDate.isBlank() && !"null".equals(rawDate)) {
            try {
                date = LocalDate.parse(rawDate.trim());
            } catch (DateTimeParseException ignored) {
                date = null;
            }
        }
        if (date == null && fillToday) {
            date = base;
        }

        Long categoryId = null;
        JsonNode c = r.path("categoryId");
        if (c.isIntegralNumber() && contains(categories, c.asLong())) {
            categoryId = c.asLong();
        }
        if (categoryId == null) {
            categoryId = etcExpenseId(categories);
        }

        String memo = r.path("memo").isString() ? r.path("memo").asString().strip() : "";
        if (memo.length() > MEMO_MAX) {
            memo = memo.substring(0, MEMO_MAX);
        }

        return new ParsedTransaction(amount, date, categoryId, memo.isEmpty() ? null : memo);
    }

    /**
     * 지출 "기타"의 id. "기타"는 INCOME·EXPENSE 양쪽에 있어서 이름만으로는 가려낼 수 없다
     * (그래서 모델에게도 id 로 고르게 한다). 목록에 없으면 {@code null}.
     */
    public static Long etcExpenseId(List<CategoryResponse> categories) {
        return categories.stream()
                .filter(c -> ETC_NAME.equals(c.name()) && CategoryType.EXPENSE.name().equals(c.type()))
                .map(CategoryResponse::id)
                .findFirst()
                .orElse(null);
    }

    private static boolean contains(List<CategoryResponse> categories, long id) {
        return categories.stream().anyMatch(c -> c.id() == id);
    }
}
