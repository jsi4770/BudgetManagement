package com.back.was.domain.ocr;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OCR 서버가 아직 없는 동안의 {@link ReceiptOcrClient} 구현체 — 항상
 * {@code 502 OCR_FAILED} 다.
 *
 * <p><b>가짜 텍스트를 돌려주지 않는다.</b> 그럴듯한 영수증 문자열을 만들면 프론트에서는
 * 동작하는 것처럼 보이고, OCR 서버가 아직 없다는 사실이 숨는다 — TX-07 의
 * {@code UnavailableTransactionTextParser} 와 같은 판단이고 CLAUDE.md 에도 그렇게 적혀
 * 있다. 502 는 명세 RCPT-01 이 OCR 실패에 정한 코드이고, 화면 문구도 이미 준비돼 있다
 * ({@code docs/AI_대체_문구.md} 8장: "영수증을 읽지 못했어요. 밝은 곳에서 다시 찍거나
 * 직접 입력해 주세요.").
 *
 * <p><b>HTTP 어댑터를 추가할 때는 이 클래스를 삭제하라.</b> 남겨 두고 새 구현체에
 * {@code @Primary} 를 붙이는 방법도 되지만, 빈이 둘이면 "어느 쪽이 쓰이는지"가 애너테이션에
 * 숨는다.
 *
 * <p>어댑터가 들어올 자리를 미리 적어 두면: 이 패키지에
 * {@code HttpReceiptOcrClient implements ReceiptOcrClient} 를 만들고
 * {@code RestClient}(Boot 4 권장) 로 Django 에 {@code multipart} 로 이미지를 올리고,
 * 응답의 텍스트를 꺼내 돌려준다. 실패·타임아웃을
 * {@code BusinessException(OCR_FAILED)} 로 바꾸는 것이 어댑터의 몫이다
 * ({@link ReceiptOcrClient} 의 계약). 엔드포인트 주소·인증·타임아웃은 환경 변수로 받고
 * 기본값을 두지 않는다 — OCR 담당과 협의할 항목이다(서버_내부_처리 9장).
 *
 * <p>그때까지 RCPT-01 은 <b>경로·검증·응답 계약이 모두 완성된 상태로</b> 502 를 낸다.
 * OCR 서버가 붙으면 그다음 단계(Gemini)가 아직 없어 503 {@code AI_UNAVAILABLE} 로
 * 바뀌고, 둘 다 붙으면 그대로 동작한다.
 */
@Slf4j
@Component
public class UnavailableReceiptOcrClient implements ReceiptOcrClient {

    @Override
    public String extractText(ReceiptImage image) {
        // 이미지와 원문은 로그에 남기지 않는다 (SNFR-008) — 크기만 남긴다.
        log.debug("OCR 서버가 없어 502 로 응답 ({} bytes)", image.bytes().length);
        throw new BusinessException(ErrorCode.OCR_FAILED);
    }
}
