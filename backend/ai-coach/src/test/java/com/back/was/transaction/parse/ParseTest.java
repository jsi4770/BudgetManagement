package com.back.was.transaction.parse;

import com.back.was.category.CategoryProviderImpl;
import com.back.was.common.AiParseFailedException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 후처리·날짜표 테스트 (Gemini 호출 없이) */
class ParseTest {

    private final ObjectMapper om = new ObjectMapper();
    private final ParsedTxPostProcessor post = new ParsedTxPostProcessor(new CategoryProviderImpl());
    private final LocalDate base = LocalDate.of(2026, 11, 10);   // 화요일

    @Test
    void 날짜표_저번주_수요일() {
        String table = ParsePromptBuilder.dateTable(base);
        assertThat(table).contains("저번 주(지난주): 월 2026-11-02, 화 2026-11-03, 수 2026-11-04");
        assertThat(table).contains("오늘(작성일): 2026-11-10 (화)");
    }

    @Test
    void 금액_없으면_missingFields_amount() throws Exception {
        ParseResult r = post.process(om.readTree(
                "{\"isTransaction\":true,\"amount\":null,\"transactionDate\":null,\"categoryId\":1,\"memo\":\"점심 김치찌개\"}"),
                base, true);
        assertThat(r.missingFields()).containsExactly("amount");
        assertThat(r.transactionDate()).isEqualTo("2026-11-10");   // 날짜 없으면 작성일
    }

    @Test
    void 목록에_없는_카테고리는_기타() throws Exception {
        ParseResult r = post.process(om.readTree(
                "{\"isTransaction\":true,\"amount\":13000,\"transactionDate\":\"2026-11-04\",\"categoryId\":999,\"memo\":\"옷\"}"),
                base, true);
        assertThat(r.category().name()).isEqualTo("기타");
        assertThat(r.amount()).isEqualTo(13000L);
    }

    @Test
    void 거래정보_없으면_422() throws Exception {
        assertThatThrownBy(() -> post.process(om.readTree("{\"isTransaction\":false}"), base, true))
                .isInstanceOf(AiParseFailedException.class);
    }

    @Test
    void 영수증은_날짜_못찾으면_null() throws Exception {
        ParseResult r = post.process(om.readTree(
                "{\"isTransaction\":true,\"amount\":23500,\"transactionDate\":null,\"categoryId\":2,\"memo\":\"배달의민족\"}"),
                base, false);
        assertThat(r.missingFields()).containsExactly("transactionDate");
    }
}
