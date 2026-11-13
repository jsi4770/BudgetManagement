package com.back.was.domain.ocr;

import com.back.was.domain.ai.ParsedTransaction;
import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.exception.BusinessException;

import java.util.List;

/**
 * 영수증 분석 포트 (RCPT-01 · 서버_내부_처리 9장 2단계).
 *
 * <p>이미지를 받아 거래 정보({@link ParsedTransaction})까지 돌려준다. 구현은 둘이고 설정으로 갈린다:
 * <ul>
 *   <li>{@link DjangoReceiptOcrClient} — {@code ocr.base-url} 이 있을 때. 별도 OCR 서비스
 *       ({@code backend/ocr}, Vertex AI 멀티모달)가 이미지에서 금액·날짜·카테고리·메모를 직접 뽑아 준다.</li>
 *   <li>{@link GeminiReceiptOcrClient} — {@code ocr.base-url} 이 없을 때. Gemini 가 이미지를 직접
 *       읽는 대체 경로(개발·시연용).</li>
 * </ul>
 *
 * <h2>계약</h2>
 * <ul>
 *   <li>OCR 서버 호출 실패·타임아웃·응답 이상 → {@code BusinessException(OCR_FAILED)} (502)</li>
 *   <li>AI 쪽 한도 초과·장애·키 없음 → {@code BusinessException(AI_UNAVAILABLE)} (503)</li>
 *   <li>읽을 수 있었지만 일부가 비면 <b>예외가 아니라</b> 그 필드를 {@code null} 로 둔다.
 *       {@code missingFields} 는 서비스({@code TransactionService#assemble})가 만든다 — 모델·OCR 서버가
 *       준 목록을 믿지 않는다.</li>
 *   <li><b>이미지·인식 원문을 로그에 남기지 않는다</b> (SNFR-008 · 9장). 디버깅은 길이·처리 시간만.</li>
 *   <li>타임아웃을 반드시 설정한다.</li>
 * </ul>
 */
public interface ReceiptOcrClient {

    /**
     * @param categories 모델이 고를 수 있는 카테고리 전체(DB 목록). {@code id} 로 고르게 한다
     * @throws BusinessException 502 {@code OCR_FAILED} / 503 {@code AI_UNAVAILABLE} / 422 {@code AI_PARSE_FAILED}
     */
    ParsedTransaction analyze(ReceiptImage image, List<CategoryResponse> categories);
}
