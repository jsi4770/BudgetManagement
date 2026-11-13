package com.back.was.transaction.parse;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.common.InvalidInputException;
import com.back.was.common.TimeUtil;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * TX-07 자연어 분석. 저장하지 않고 결과만 반환 (SFR-008).
 * 실패: 400 VALIDATION_ERROR(길이) / 422 AI_PARSE_FAILED / 503 AI_UNAVAILABLE
 */
@Service
public class TransactionParseService {

    private static final Logger log = LoggerFactory.getLogger(TransactionParseService.class);
    private static final int TEXT_MAX = 200;

    private final GeminiClient gemini;
    private final ParsePromptBuilder promptBuilder;
    private final ParsedTxPostProcessor postProcessor;

    public TransactionParseService(GeminiClient gemini, ParsePromptBuilder promptBuilder,
                                   ParsedTxPostProcessor postProcessor) {
        this.gemini = gemini;
        this.promptBuilder = promptBuilder;
        this.postProcessor = postProcessor;
    }

    /** 작성일 = 요청이 들어온 오늘 (KST) */
    public ParseResult parse(String text) {
        return parse(text, TimeUtil.today());
    }

    /** 작성일을 직접 지정 (테스트용) */
    public ParseResult parse(String text, LocalDate writtenDate) {
        String input = text == null ? "" : text.strip();
        if (input.isEmpty() || input.length() > TEXT_MAX) {
            throw new InvalidInputException("입력은 1~200자여야 해요.", "text", "LENGTH");
        }

        JsonNode r = gemini.generateJson(
                promptBuilder.build(writtenDate),
                "입력: " + input,
                ParsedTxSchema.SCHEMA,
                0.0);   // 파싱은 항상 같은 답이 나오게

        log.debug("[TX-07] '{}' → 교정='{}', 금액근거='{}'", input,
                r.path("correctedText").asString(""), r.path("amountReasoning").asString(""));

        return postProcessor.process(r, writtenDate, true);
    }
}
