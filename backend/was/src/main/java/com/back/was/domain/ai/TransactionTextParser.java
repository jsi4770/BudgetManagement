package com.back.was.domain.ai;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;

/**
 * 자연어 한 줄에서 거래 정보를 뽑는 포트 (TX-07, 그리고 OCR 텍스트를 쓰는 RCPT-01).
 *
 * <p>구현체를 갈아끼울 수 있게 인터페이스로 둔 건 취향이 아니라 역할 분담 때문이다.
 * 서버_내부_처리 8장은 <b>프롬프트와 구조화 출력 스키마를 "AI 담당이 정의"</b>한다고
 * 못박았고, 백엔드가 지켜야 하는 건 명세 TX-07 의 응답 필드와 실패 코드다. 그 경계가
 * 이 인터페이스다 — 바깥(서비스·컨트롤러)은 모델도 SDK 도 모르고, 안쪽(어댑터)은 DB 도
 * HTTP 도 모른다.
 *
 * <p><b>구현 시 지켜야 할 계약</b>
 * <ul>
 *   <li>타임아웃은 10초 (서버_내부_처리 8장). 초과는 호출 실패로 간주한다.</li>
 *   <li>호출 실패·한도 초과·타임아웃 → {@code BusinessException(ErrorCode.AI_UNAVAILABLE)}
 *       (503). 명세 TX-07 Errors.</li>
 *   <li>응답이 왔지만 JSON·스키마가 깨져 읽을 수 없으면 →
 *       {@code BusinessException(ErrorCode.AI_PARSE_FAILED)} (422).</li>
 *   <li>읽을 수는 있으나 일부 필드가 비었으면 <b>예외가 아니라</b> 그 필드를
 *       {@code null} 로 둔 {@link ParsedTransaction} 을 돌려준다. 전부 비어 있을 때
 *       422 로 바꾸는 판단은 정책이므로 서비스가 한다.</li>
 *   <li>API 키는 환경 변수로만 받는다 (서버_내부_처리 8장). 코드·저장소에 두지 않는다.</li>
 * </ul>
 *
 * <p>구현체는 현재 {@link UnavailableTransactionTextParser} 하나뿐이고 항상 503 이다.
 * Spring AI(Boot 4 는 2.0.x 계열) 어댑터를 붙일 때 그 클래스를 대체하면 TX-07 ·
 * RCPT-01 의 나머지 코드는 건드릴 필요가 없다.
 *
 * @see ErrorCode#AI_UNAVAILABLE
 * @see ErrorCode#AI_PARSE_FAILED
 */
public interface TransactionTextParser {

    /**
     * @throws BusinessException {@code AI_UNAVAILABLE}(503) 또는 {@code AI_PARSE_FAILED}(422)
     */
    ParsedTransaction parse(TextParseCommand command);
}
