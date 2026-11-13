package com.back.was.domain.ai;

import com.back.was.domain.ai.gemini.GeminiClient;
import com.back.was.global.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * {@link TransactionTextParser} 의 Gemini 구현 (TX-07).
 *
 * <p>DB 를 모른다 — 필요한 것(오늘, 카테고리 목록)은 전부 {@link TextParseCommand} 로 받는다.
 * 실패는 포트 계약대로 {@code BusinessException}:
 * <ul>
 *   <li>호출 실패·한도 초과·타임아웃·키 없음 → 503 {@code AI_UNAVAILABLE}</li>
 *   <li>응답은 왔지만 읽을 수 없거나 거래 정보가 아님 → 422 {@code AI_PARSE_FAILED}</li>
 * </ul>
 *
 * <p><b>사용자 입력은 로그에 남기지 않는다.</b> 가계부 메모에는 개인 정보가 섞일 수 있다.
 */
@Component
public class GeminiTransactionTextParser implements TransactionTextParser {

    private final GeminiClient gemini;
    private final ParsePromptBuilder promptBuilder;

    public GeminiTransactionTextParser(GeminiClient gemini, ParsePromptBuilder promptBuilder) {
        this.gemini = gemini;
        this.promptBuilder = promptBuilder;
    }

    /** @throws BusinessException 503 {@code AI_UNAVAILABLE} / 422 {@code AI_PARSE_FAILED} */
    @Override
    public ParsedTransaction parse(TextParseCommand command) {
        JsonNode r = gemini.generateJson(
                promptBuilder.build(command.today(), command.categories()),
                "입력: " + command.text(),
                ParsedTxSchema.SCHEMA,
                0.0);   // 파싱은 항상 같은 답이 나오게

        return ParsedTxMapper.toParsed(r, command.today(), true, command.categories());
    }
}
