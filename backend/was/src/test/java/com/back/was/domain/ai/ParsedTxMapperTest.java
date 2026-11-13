package com.back.was.domain.ai;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static com.back.was.domain.ai.AiTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini JSON → {@link ParsedTransaction} 정규화. 포트 계약: 못 뽑은 필드는 null, 미래 날짜는 그대로. */
class ParsedTxMapperTest {

    private static final LocalDate BASE = LocalDate.of(2026, 11, 10);   // 화요일

    private static ParsedTransaction map(String jsonText, boolean fillToday) {
        return ParsedTxMapper.toParsed(json(jsonText), BASE, fillToday, CATEGORIES);
    }

    @Test
    @DisplayName("정상 값은 그대로 ParsedTransaction 이 된다")
    void mapsAllFields() {
        ParsedTransaction p = map("""
                {"isTransaction":true,"amount":18000,"transactionDate":"2026-11-09","categoryId":2,"memo":"저녁 배달"}
                """, true);

        assertThat(p).isEqualTo(new ParsedTransaction(18000, LocalDate.of(2026, 11, 9), 2L, "저녁 배달"));
    }

    @Test
    @DisplayName("금액이 없으면 null — missingFields 는 서비스가 이 null 로 만든다")
    void missingAmountIsNull() {
        ParsedTransaction p = map("""
                {"isTransaction":true,"amount":null,"transactionDate":null,"categoryId":1,"memo":"점심 김치찌개"}
                """, true);

        assertThat(p.amount()).isNull();
        assertThat(p.transactionDate()).isEqualTo(BASE);   // 한 줄 입력은 날짜가 없으면 작성일
    }

    @Test
    @DisplayName("0·음수·소수·int 범위 밖 금액은 null (TX-01 은 1 이상 정수만 받는다)")
    void invalidAmountsAreNull() {
        for (String amount : new String[]{"0", "-500", "1.5", "99999999999"}) {
            assertThat(map("""
                    {"isTransaction":true,"amount":%s,"transactionDate":"2026-11-09","categoryId":1,"memo":null}
                    """.formatted(amount), true).amount())
                    .as("amount=%s", amount).isNull();
        }
    }

    @Test
    @DisplayName("영수증(fillToday=false)은 날짜를 못 읽으면 오늘로 채우지 않고 null")
    void receiptDoesNotFillToday() {
        ParsedTransaction p = map("""
                {"isTransaction":true,"amount":23500,"transactionDate":null,"categoryId":2,"memo":"배달의민족"}
                """, false);

        assertThat(p.transactionDate()).isNull();
    }

    @Test
    @DisplayName("미래 날짜도 그대로 둔다 — 거부는 저장 시점(TX-01)의 몫")
    void futureDateIsKept() {
        ParsedTransaction p = map("""
                {"isTransaction":true,"amount":1000,"transactionDate":"2099-01-01","categoryId":1,"memo":null}
                """, true);

        assertThat(p.transactionDate()).isEqualTo(LocalDate.of(2099, 1, 1));
    }

    @Test
    @DisplayName("형식이 깨진 날짜는 null")
    void malformedDateIsNull() {
        assertThat(map("""
                {"isTransaction":true,"amount":1000,"transactionDate":"어제","categoryId":1,"memo":null}
                """, false).transactionDate()).isNull();
    }

    @Test
    @DisplayName("목록에 없는 카테고리 id 는 지출 '기타'(6) — 수입 '기타'(9)가 아니다")
    void unknownCategoryFallsBackToExpenseEtc() {
        assertThat(map("""
                {"isTransaction":true,"amount":13000,"transactionDate":"2026-11-04","categoryId":999,"memo":"옷"}
                """, true).categoryId()).isEqualTo(6L);
        assertThat(map("""
                {"isTransaction":true,"amount":13000,"transactionDate":"2026-11-04","categoryId":null,"memo":"옷"}
                """, true).categoryId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("수입 카테고리를 고른 것은 존중한다 (급여)")
    void incomeCategoryIsKept() {
        assertThat(map("""
                {"isTransaction":true,"amount":2500000,"transactionDate":"2026-11-10","categoryId":7,"memo":"월급"}
                """, true).categoryId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("etcExpenseId 는 이름이 같은 수입 '기타'와 헷갈리지 않는다")
    void etcExpenseIdIgnoresIncomeEtc() {
        assertThat(ParsedTxMapper.etcExpenseId(CATEGORIES)).isEqualTo(6L);
    }

    @Test
    @DisplayName("메모는 공백을 정리하고 255자로 자르며, 비면 null")
    void memoIsNormalized() {
        assertThat(map("""
                {"isTransaction":true,"amount":1000,"transactionDate":"2026-11-09","categoryId":1,"memo":"   "}
                """, true).memo()).isNull();

        String longMemo = "가".repeat(300);
        assertThat(map("""
                {"isTransaction":true,"amount":1000,"transactionDate":"2026-11-09","categoryId":1,"memo":"%s"}
                """.formatted(longMemo), true).memo()).hasSize(255);
    }

    @Test
    @DisplayName("거래 정보가 아니라고 판단하면 422 AI_PARSE_FAILED")
    void notATransactionIsUnprocessable() {
        assertThatThrownBy(() -> map("{\"isTransaction\":false}", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_PARSE_FAILED));
        assertThatThrownBy(() -> ParsedTxMapper.toParsed(null, BASE, true, CATEGORIES))
                .isInstanceOf(BusinessException.class);
    }
}
