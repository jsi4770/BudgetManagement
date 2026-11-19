package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.domain.transaction.TransactionService;
import com.back.was.domain.transaction.dto.ParsedTransactionResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * RCPT-01 영수증 OCR 분석 (명세 7장 · 서버_내부_처리 9장).
 *
 * <p>서버가 <b>한 번에</b> 처리한다: 이미지 검사 → 분석({@link ReceiptOcrClient}) → TX-07 과 같은 응답.
 * 응답 조립은 {@link TransactionService#assemble} 를 그대로 쓴다 — {@code missingFields} 규칙과 422 조건이
 * 두 벌이 되면 같은 확인 화면에 서로 다른 계약이 내려간다.
 *
 * <p><b>아무것도 저장하지 않는다</b>(SNFR-008 · 9장): 이미지는 메모리에서 분석기로 넘어가고 응답 후
 * 버려진다. 이 클래스에 리포지터리가 없고 이미지·인식 원문을 로그에 남기지 않는다.
 *
 * <p>용량(10MB)은 여기서 보지 않는다 — {@code spring.servlet.multipart.max-file-size} 가 컨트롤러 앞에서
 * 끊고, 그 예외가 {@code GlobalExceptionHandler} 에서 413 {@code FILE_TOO_LARGE} 가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrService {

    /**
     * 명세 RCPT-01: "jpg / png". HEIC 는 프론트가 JPG 로 바꿔 보내고, 변환하지 않은 HEIC 는 415 다.
     * {@code image/jpg} 는 표준이 아니지만 일부 클라이언트가 보내서 함께 받는다.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final ReceiptOcrClient receiptOcrClient;
    private final TransactionService transactionService;

    /**
     * @throws BusinessException 415 {@code UNSUPPORTED_FILE_TYPE} / 502 {@code OCR_FAILED} /
     *                           503 {@code AI_UNAVAILABLE} / 422 {@code AI_PARSE_FAILED}
     */
    public ParsedTransactionResponse analyze(MultipartFile image) {
        ReceiptImage receipt = toReceiptImage(image);

        List<CategoryResponse> categories = transactionService.selectableCategories();
        ParsedTransaction parsed = receiptOcrClient.analyze(receipt, categories);

        return transactionService.assemble(parsed, categories);
    }

    /**
     * 빈 파일도 415 로 막는다. 0바이트를 분석기에 보내 봐야 실패하고, 그 실패는 502("사진을 다시
     * 찍어 보세요")로 나가는데 실제 문제는 파일이 비었다는 것이다. 파일명·내용은 로그에 남기지 않는다.
     */
    private static ReceiptImage toReceiptImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (image.isEmpty() || contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            log.debug("지원하지 않는 영수증 형식: {}", contentType);
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        try {
            return new ReceiptImage(image.getBytes(), contentType);
        } catch (IOException e) {
            log.warn("영수증 이미지를 읽지 못했어요: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OCR_FAILED);
        }
    }
}
