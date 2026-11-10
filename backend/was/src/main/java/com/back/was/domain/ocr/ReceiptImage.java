package com.back.was.domain.ocr;

/**
 * OCR 서버로 보낼 영수증 이미지 — {@link ReceiptOcrClient} 의 입력.
 *
 * <p><b>Spring 의 {@code MultipartFile} 을 넘기지 않는다.</b> 그 타입은 서블릿 요청에
 * 묶여 있어서, 포트가 그걸 받으면 어댑터를 웹 요청 밖에서 테스트할 수 없고 나중에 OCR
 * 서버를 다른 경로(배치·재시도 큐)로 부를 때도 걸린다. 어댑터가 실제로 필요한 것은
 * <b>바이트와 타입</b> 두 개뿐이다.
 *
 * <p>바이트를 메모리에 들고 있는 것은 괜찮다 — 명세 RCPT-01 의 상한이 10MB 이고,
 * 서버_내부_처리 9장이 <b>디스크·DB·외부 저장소 어디에도 저장하지 말라</b>고 했으므로
 * 임시 파일로 떨어뜨릴 수도 없다. 응답이 끝나면 GC 대상이 된다(9장 "이미지 즉시 폐기").
 *
 * <p>{@code toString} 을 쓰지 말 것. <b>이미지와 OCR 원문은 로그에도 남기지 않는다</b>
 * (SNFR-008 · 9장) — 레코드의 기본 {@code toString} 은 바이트 배열의 식별자만 찍지만,
 * 필드를 늘릴 때 원문이 섞여 들어갈 수 있으니 애초에 찍지 않는 습관이 안전하다.
 *
 * @param bytes       이미지 원본 바이트
 * @param contentType {@code image/jpeg} 또는 {@code image/png} — 검증을 통과한 값만 온다
 */
public record ReceiptImage(
        byte[] bytes,
        String contentType
) {
    /** 로그·디버거에 원본이 새지 않도록 크기만 노출한다. */
    @Override
    public String toString() {
        return "ReceiptImage[contentType=%s, bytes=%d]".formatted(contentType, bytes.length);
    }
}
