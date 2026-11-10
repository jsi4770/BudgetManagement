package com.back.was.domain.ocr;

import com.back.was.domain.transaction.dto.ParsedTransactionResponse;
import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이 프로젝트에서 <b>유일한 {@code multipart/form-data} 엔드포인트</b>다. 그래서
 * {@code spring.servlet.multipart.*} 설정과 {@code MaxUploadSizeExceededException} 핸들러도
 * 여기만을 위한 것이다.
 *
 * <p>Django 호출은 {@link ReceiptOcrClient} 구현체 안에 숨는다 — 컨트롤러는 OCR 서버를
 * 모른다.
 */
@Tag(name = "7. 영수증 OCR (RCPT)", description = """
        사진 한 장으로 거래 정보를 뽑습니다. 이 프로젝트의 **유일한 `multipart/form-data`** 엔드포인트입니다.

        이미지와 OCR 원문은 **어디에도 저장하지 않습니다** — 디스크·DB·로그 전부 포함입니다.""")
@RestController
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptOcrController {

    private final ReceiptOcrService receiptOcrService;

    /**
     * {@code @RequestPart} 라서 {@code image} 파트가 없으면
     * {@code MissingServletRequestPartException} → 400
     * {@code VALIDATION_ERROR} + {@code details[{field: image, reason: REQUIRED}]} 다.
     * 그 매핑을 {@code GlobalExceptionHandler} 에 추가해 둬야 500 이 되지 않는다.
     *
     * <p>{@code consumes} 를 명시하면 다른 {@code Content-Type} 으로 온 요청이
     * {@code HttpMediaTypeNotSupportedException} → 415 가 된다 — 명세 RCPT-01 의
     * {@code UNSUPPORTED_FILE_TYPE} 과 같은 코드라 결과가 자연스럽게 맞는다. 파일 자체의
     * 형식(jpg/png) 검사는 서비스가 한다.
     */
    @Operation(summary = "RCPT-01 영수증 OCR 분석", description = """
            이미지 → OCR → 지출 정보 추출까지 **서버가 한 번에** 처리합니다.
            프론트는 TX-07 을 따로 부르지 않고, 응답 형식이 TX-07 과 같아서 확인 화면을 재사용할 수 있습니다.

            `image` 파트에 jpg 또는 png, 최대 10MB. 아이폰 HEIC 는 **프론트에서 JPG 로 변환**해 보내세요
            (미변환 HEIC 는 415). 결과를 돌려준 뒤 이미지는 즉시 폐기됩니다.

            > ⚠️ **현재 OCR 서버가 없어 항상 `502 OCR_FAILED` 입니다.**
            > OCR 서버가 붙으면 다음 단계(Gemini)가 없어 `503` 으로 바뀌고, 둘 다 붙으면 정상 동작합니다.
            > 즉 **응답 코드로 어느 쪽이 빠졌는지 알 수 있습니다.**

            **실패**: `400 VALIDATION_ERROR`(`image` 파트 없음), `413 FILE_TOO_LARGE`,
            `415 UNSUPPORTED_FILE_TYPE`(jpg·png 아님, 빈 파일), `502 OCR_FAILED`,
            `503 AI_UNAVAILABLE`, `422 AI_PARSE_FAILED`""")
    @PostMapping(path = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ParsedTransactionResponse> analyze(@RequestPart("image") MultipartFile image) {
        return ApiResponse.ok(receiptOcrService.analyze(image));
    }
}
