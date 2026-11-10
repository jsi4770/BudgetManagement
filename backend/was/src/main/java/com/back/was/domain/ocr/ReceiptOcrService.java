package com.back.was.domain.ocr;

import com.back.was.domain.transaction.TransactionService;
import com.back.was.domain.transaction.dto.ParsedTransactionResponse;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * RCPT-01 영수증 OCR 분석 (명세 7장 · 서버_내부_처리 9장).
 *
 * <p>서버가 <b>한 번에</b> 처리한다: 이미지 검사 → OCR → 거래 정보 정리. 프론트는 TX-07 을
 * 따로 부르지 않고(명세 RCPT-01) 응답은 TX-07 과 같은 모양이라 확인·수정 화면을 그대로
 * 재사용한다.
 *
 * <p><b>아무것도 저장하지 않는다</b>(SNFR-008 · 9장). 이미지는 메모리에서 OCR 로 넘어가고
 * 응답 후 버려지며, OCR 원문도 보관하지 않는다 — <b>로그에도 남기지 않는다</b>. 그래서 이
 * 클래스에 리포지터리가 하나도 없다.
 *
 * <p>두 단계가 각각 다른 외부에 의존하고 <b>실패 코드도 다르다</b>:
 *
 * <ul>
 *   <li>OCR({@link ReceiptOcrClient}) 실패 → 502 {@code OCR_FAILED}</li>
 *   <li>정리(Gemini, {@code TransactionTextParser}) 실패 → 503 {@code AI_UNAVAILABLE},
 *       읽었는데 아무 값도 못 뽑으면 422 {@code AI_PARSE_FAILED}</li>
 * </ul>
 *
 * <p>지금은 OCR 어댑터가 없어 <b>항상 502</b> 다
 * ({@link UnavailableReceiptOcrClient}). OCR 서버가 붙으면 다음 단계가 없어 503 으로
 * 바뀌고, Gemini 까지 붙으면 그대로 동작한다 — 경로·검증·응답 계약은 이미 완성돼 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrService {

    /**
     * 명세 RCPT-01: "jpg / png". HEIC 는 프론트가 JPG 로 바꿔 보내고, 변환하지 않은 HEIC 는
     * 415 다 — 그래서 허용 목록을 좁게 둔다.
     *
     * <p>{@code image/jpg} 를 함께 받는다. 표준 타입은 {@code image/jpeg} 지만 일부
     * 클라이언트가 전자를 보내고, 거부하면 사용자에게는 "왜 안 되는지 알 수 없는 415" 다.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png");

    private final ReceiptOcrClient receiptOcrClient;
    private final TransactionService transactionService;

    /**
     * 서버_내부_처리 9장의 네 단계를 순서대로 돈다.
     *
     * <p>용량(10MB)은 여기서 보지 않는다 — {@code spring.servlet.multipart.max-file-size}
     * 가 상한을 넘는 요청을 컨트롤러 앞에서 끊고, 그 예외가
     * {@code GlobalExceptionHandler} 에서 413 {@code FILE_TOO_LARGE} 가 된다. 서비스에서
     * 한 번 더 세면 같은 규칙이 두 군데가 되고, 그중 하나(설정)는 바꿔도 코드가 모른다.
     *
     * <p><b>빈 텍스트를 502 로 바꾼다.</b> OCR 호출은 성공했지만 글자를 하나도 못 읽은
     * 경우인데, 그걸 그대로 다음 단계에 넘기면 "아무것도 추출하지 못함"이 422
     * {@code AI_PARSE_FAILED}("입력에서 정보를 읽지 못했어요")로 나간다 — 사용자가 고쳐야
     * 할 것은 입력이 아니라 사진이므로 502 쪽 안내가 맞다
     * ({@code docs/AI_대체_문구.md} 8장: "밝은 곳에서 다시 찍거나 직접 입력해 주세요").
     *
     * @throws BusinessException 415 {@code UNSUPPORTED_FILE_TYPE} /
     *                           502 {@code OCR_FAILED} /
     *                           503 {@code AI_UNAVAILABLE} / 422 {@code AI_PARSE_FAILED}
     */
    public ParsedTransactionResponse analyze(MultipartFile image) {
        ReceiptImage receipt = toReceiptImage(image);

        String text = receiptOcrClient.extractText(receipt);
        if (!StringUtils.hasText(text)) {
            log.debug("OCR 이 읽은 글자가 없어 502 로 응답");
            throw new BusinessException(ErrorCode.OCR_FAILED);
        }

        return transactionService.parseText(text);
    }

    /**
     * 9장 1단계의 형식 검사 + 포트가 쓸 모양으로 변환.
     *
     * <p>빈 파일도 415 로 막는다. 0바이트를 OCR 서버에 보내 봐야 실패하고, 그 실패는 502 로
     * 나가서 "사진을 다시 찍어 보세요"가 되는데 실제 문제는 파일이 비었다는 것이다.
     *
     * <p>{@code IOException} 은 요청 본문을 읽다 끊긴 경우다 — 서버 잘못이 아니지만 명세에
     * 그 코드가 없고, 사용자가 할 수 있는 일(다시 올리기)은 502 안내와 같아서 502 로 모은다.
     */
    private static ReceiptImage toReceiptImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (image.isEmpty() || contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            // 파일명·내용은 남기지 않는다. 타입만으로 원인을 알 수 있다.
            log.debug("지원하지 않는 영수증 형식: {}", contentType);
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        try {
            return new ReceiptImage(image.getBytes(), contentType);
        } catch (IOException e) {
            log.warn("영수증 이미지를 읽지 못했어요", e);
            throw new BusinessException(ErrorCode.OCR_FAILED);
        }
    }
}
