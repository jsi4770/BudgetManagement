package com.back.was.domain.ai;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 어댑터가 아직 없는 동안의 {@link TransactionTextParser} 구현체 — 항상
 * {@code 503 AI_UNAVAILABLE} 이다.
 *
 * <p>스텁이 아니라 <b>정직한 응답</b>이다. Gemini 연동이 없는 서버는 자연어 분석을
 * 제공할 수 없고, 명세 TX-07 이 그 상황에 정의한 코드가 바로 503 이다(프론트는 직접 입력
 * 화면으로 안내). 가짜 추출 결과를 돌려주면 프론트가 동작하는 것처럼 보여서 미구현이
 * 숨는다.
 *
 * <p><b>Spring AI 어댑터를 추가할 때는 이 클래스를 삭제하라.</b> 남겨 두고 새 구현체에
 * {@code @Primary} 를 붙이는 방법도 되지만, 빈이 둘이면 "어느 쪽이 쓰이는지"가
 * 애너테이션에 숨는다. 조건부 등록({@code @ConditionalOnMissingBean})을 쓰지 않은 것도
 * 같은 이유다 — 일반 {@code @Configuration} 에서 그 조건은 처리 순서에 따라 결과가
 * 달라진다.
 */
@Slf4j
@Component
public class UnavailableTransactionTextParser implements TransactionTextParser {

    @Override
    public ParsedTransaction parse(TextParseCommand command) {
        log.debug("AI 자연어 분석 어댑터가 없어 503 으로 응답 (text {}자)", command.text().length());
        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
}
