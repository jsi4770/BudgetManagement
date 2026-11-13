package com.back.was.receipt;

import com.back.was.ai.gemini.GeminiClient;
import com.back.was.common.InvalidInputException;
import com.back.was.common.OcrFailedException;
import com.back.was.common.TimeUtil;
import com.back.was.transaction.parse.ParseResult;
import com.back.was.transaction.parse.ParsedTxPostProcessor;
import com.back.was.transaction.parse.ParsedTxSchema;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * RCPT-01 영수증 OCR 분석. 이미지·결과 모두 저장하지 않음 (TX-07 과 같은 방식).
 *
 * 처리 순서 (명세)
 *  1. 이미지 검증 (jpg/png, 10MB)
 *  2. Django OCR 서버 → CLOVA OCR 로 텍스트 추출
 *  3. 금액·날짜·카테고리 정리 (Gemini)
 *  4. 결과 반환, 이미지는 메모리에서만 쓰고 버림
 * Django 주소가 아직 없으면(ocr.django-url 비어 있음) Gemini 가 이미지를 직접 읽는 대체 경로 사용.
 */
@Service
public class ReceiptOcrService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrService.class);

    private final OcrTextClient ocrClient;
    private final OcrProperties props;
    private final GeminiClient gemini;
    private final ReceiptPromptBuilder promptBuilder;
    private final ParsedTxPostProcessor postProcessor;

    public ReceiptOcrService(OcrTextClient ocrClient, OcrProperties props, GeminiClient gemini,
                             ReceiptPromptBuilder promptBuilder, ParsedTxPostProcessor postProcessor) {
        this.ocrClient = ocrClient;
        this.props = props;
        this.gemini = gemini;
        this.promptBuilder = promptBuilder;
        this.postProcessor = postProcessor;
    }

    public ParseResult analyze(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidInputException("영수증 이미지가 필요해요.", "image", "REQUIRED");
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new OcrFailedException("이미지를 읽지 못했어요.", e);
        }
        String mime = ImageValidator.validateAndDetectMime(bytes);   // 413 / 415

        LocalDate today = TimeUtil.today();
        String system = promptBuilder.build(today);
        JsonNode r;

        if (ocrClient.isConfigured()) {
            String text = ocrClient.extractText(bytes, mime);           // 502 OCR_FAILED
            log.debug("[RCPT-01] OCR 텍스트 {}자", text.length());
            r = gemini.generateJson(system, "영수증 OCR 텍스트:\n" + text, ParsedTxSchema.SCHEMA, 0.0);
        } else if (props.geminiFallback()) {
            log.info("[RCPT-01] OCR 서버 미설정 → Gemini 가 이미지를 직접 읽습니다.");
            r = gemini.generateJson(system, "위 영수증 이미지에서 거래 정보를 추출해.",
                    bytes, mime, ParsedTxSchema.SCHEMA, 0.0);
        } else {
            throw new OcrFailedException("OCR 서버 설정이 없어요.");
        }

        // 영수증은 날짜를 오늘로 채우면 오히려 틀릴 수 있어서 못 찾으면 null → missingFields
        return postProcessor.process(r, today, false);
    }
}
