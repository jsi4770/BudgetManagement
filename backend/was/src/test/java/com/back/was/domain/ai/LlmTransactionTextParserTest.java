package com.back.was.domain.ai;

import com.back.was.domain.ai.AiTestFixtures.FakeLlm;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.back.was.domain.ai.AiTestFixtures.CATEGORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmTransactionTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static TextParseCommand command(String text) {
        return new TextParseCommand(text, TODAY, CATEGORIES);
    }

    @Test
    @DisplayName("LLM 결과가 포트 계약대로 ParsedTransaction 이 되고, 입력·오늘·카테고리가 프롬프트로 간다")
    void parsesThroughLlm() {
        FakeLlm llm = new FakeLlm("""
                {"isTransaction":true,"correctedText":"배민 만삼천원","amount":13000,
                 "transactionDate":"2026-11-10","categoryId":2,"memo":"배민"}
                """);
        LlmTransactionTextParser parser = new LlmTransactionTextParser(llm, new ParsePromptBuilder());

        ParsedTransaction p = parser.parse(command("뱌민 만삼처넌"));

        assertThat(p).isEqualTo(new ParsedTransaction(13000, TODAY, 2L, "배민"));
        assertThat(llm.userTexts).containsExactly("입력: 뱌민 만삼처넌");
        assertThat(llm.systemPrompts.get(0)).contains("오늘(작성일): 2026-11-10").contains("id=2 | 배달");
    }

    @Test
    @DisplayName("거래가 아닌 입력은 422 AI_PARSE_FAILED")
    void chatterIsUnprocessable() {
        LlmTransactionTextParser parser = new LlmTransactionTextParser(
                new FakeLlm("{\"isTransaction\":false}"), new ParsePromptBuilder());

        assertThatThrownBy(() -> parser.parse(command("ㅋㅋㅋ")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_PARSE_FAILED));
    }
}
