package com.back.was.domain.ocr;

import com.back.was.global.exception.BusinessException;

/**
 * 영수증 OCR 포트 (RCPT-01 · 서버_내부_처리 9장 2단계).
 *
 * <p><b>이 포트 뒤에는 별도 서비스가 온다.</b> 명세 RCPT-01: "이미지를 OCR 서버(Django,
 * 별도 서비스)로 전달 → CLOVA OCR 로 텍스트 추출". 즉 구현체는 <b>HTTP 클라이언트</b>가
 * 되고, 이 인터페이스가 그 경계다 — 서비스는 HTTP·CLOVA·Django 를 모르고, 어댑터는 DB·
 * 거래·카테고리를 모른다.
 *
 * <p>텍스트까지만 책임진다. 금액·날짜·카테고리로 정리하는 것은 다음 단계인
 * {@code TransactionTextParser}(TX-07 의 포트)가 하고, 그래서 RCPT-01 의 응답이 TX-07 과
 * 같은 모양이 된다(명세 RCPT-01 "응답 형식만 TX-07과 같음").
 *
 * <h2>계약</h2>
 * <ul>
 *   <li><b>실패는 {@code BusinessException(OCR_FAILED)}</b> — 502. 호출 실패·타임아웃·
 *       OCR 서버 오류·CLOVA 오류를 모두 이 하나로 바꾼다. 서비스가 그 구분을 쓸 데가 없고,
 *       명세 RCPT-01 이 OCR 쪽 실패에 준 코드가 502 하나다.</li>
 *   <li><b>인식된 텍스트가 없으면 빈 문자열</b>을 돌려줘도 된다 — 서비스가 그걸 502 로
 *       바꾼다({@code ReceiptOcrService} 참고). 어댑터가 "성공했지만 글자가 없음"을
 *       예외로 만들지 말지 고민하지 않아도 되게 한 것이다.</li>
 *   <li><b>이미지·OCR 원문을 로그에 남기지 않는다</b> (SNFR-008 · 9장). 디버깅이 필요하면
 *       길이나 처리 시간만 남긴다.</li>
 *   <li><b>타임아웃을 반드시 설정한다.</b> 외부 호출이고, 서버_내부_처리 9장이 Django 와의
 *       "타임아웃 협의 필요"를 남겨 두었다. 정해지기 전에는 TX-07 포트와 같은 10초를
 *       기준으로 삼으면 된다.</li>
 *   <li>서버 간 인증 방식도 9장의 협의 항목이다 — 어댑터가 가져갈 관심사이고 이 포트에는
 *       드러나지 않는다.</li>
 * </ul>
 *
 * <p>현재 유일한 구현은 {@link UnavailableReceiptOcrClient} 이고 항상 502 를 낸다.
 * <b>실제 HTTP 어댑터가 오면 그 클래스를 삭제</b>한다 — TX-07 의
 * {@code UnavailableTransactionTextParser} 와 같은 성격이다.
 */
public interface ReceiptOcrClient {

    /**
     * @return 영수증에서 읽은 텍스트. 인식된 글자가 없으면 빈 문자열일 수 있다
     * @throws BusinessException 502 {@code OCR_FAILED} — 호출 실패·타임아웃·OCR 처리 실패
     */
    String extractText(ReceiptImage image);
}
