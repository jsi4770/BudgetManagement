package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.ai.ParsedTxMapper;
import com.back.was.domain.ai.ParsedTxSchema;
import com.back.was.domain.ai.gemini.GeminiClient;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.support.KstTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

/**
 * Django OCR 서버가 없을 때({@code ocr.base-url} 비어 있음) Gemini 가 영수증 이미지를 직접 읽는
 * 대체 경로. 개발·시연에서 OCR 서버 없이도 RCPT-01 이 동작하게 한다.
 *
 * <p>영수증은 날짜를 오늘로 채우면 오히려 틀릴 수 있어서 못 읽으면 {@code null}
 * ({@code missingFields} 로 알린다). 이미지는 메모리에서만 쓰고 저장하지 않는다.
 *
 * <p>보내기 전에 {@link ReceiptImageScaler} 로 줄이고 회전을 바로잡는다. 깨진 파일은 AI 를 부르기 전에
 * 415 {@code UNSUPPORTED_FILE_TYPE} 이 된다.
 */
@Component
@ConditionalOnExpression(OcrConfig.DJANGO_NOT_CONFIGURED)
public class GeminiReceiptOcrClient implements ReceiptOcrClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiReceiptOcrClient.class);

    private final GeminiClient gemini;
    private final ReceiptPromptBuilder promptBuilder;

    public GeminiReceiptOcrClient(GeminiClient gemini, ReceiptPromptBuilder promptBuilder) {
        this.gemini = gemini;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public ParsedTransaction analyze(ReceiptImage image, List<CategoryResponse> categories) {
        log.info("[RCPT-01] OCR 서버 미설정 → Gemini 가 이미지를 직접 읽습니다.");
        ReceiptImage fitted = ReceiptImageScaler.fitForAi(image);   // 읽을 수 없는 파일이면 여기서 415
        log.debug("[RCPT-01] 이미지 {} → {} bytes", image.bytes().length, fitted.bytes().length);
        LocalDate today = KstTime.today();

        JsonNode r = gemini.generateJson(
                promptBuilder.build(today, categories),
                "위 영수증 이미지에서 거래 정보를 추출해.",
                fitted.bytes(), fitted.contentType(),
                ParsedTxSchema.SCHEMA, 0.0);

        return ParsedTxMapper.toParsed(r, today, false, categories);
    }
}
