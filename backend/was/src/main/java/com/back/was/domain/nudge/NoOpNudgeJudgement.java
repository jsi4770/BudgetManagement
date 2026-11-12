package com.back.was.domain.nudge;

import com.back.was.domain.category.Category;
import com.back.was.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link NudgeJudgement} 의 자리 채우기 — <b>아무 일도 하지 않는다.</b>
 *
 * <p>넛지 판정이 별도 담당자의 작업으로 넘어가 있는 동안 {@code TransactionService} 가
 * 주입받을 빈이 필요해서 있는 클래스다. 빈이 하나도 없으면 애플리케이션 컨텍스트가 아예
 * 뜨지 않는다.
 *
 * <p><b>구현이 들어오면 이 클래스를 지울 것.</b> {@code @Primary} 나
 * {@code @ConditionalOnMissingBean} 으로 덮지 말 것 — 같은 포트에 구현이 둘 있으면 어느
 * 쪽이 도는지가 설정에 숨고, "판정이 안 돈다"가 조용한 버그가 된다. 이 레포는
 * {@code UnavailableTransactionTextParser}(TX-07)·
 * {@code UnavailableReceiptOcrClient}(RCPT-01) 에서 같은 규칙을 쓴다.
 *
 * <p>그 둘과 달리 <b>여기서는 예외를 던지지 않는다.</b> 거래 등록(TX-01)은 넛지가 없어도
 * 성공해야 하는 요청이고, 후처리에서 던지면 같은 트랜잭션이라 거래 저장까지 롤백된다.
 * 그래서 "정직한 실패" 대신 "조용한 생략"이 맞고, 대신 호출될 때마다 로그를 남긴다.
 */
@Slf4j
@Component
public class NoOpNudgeJudgement implements NudgeJudgement {

    @Override
    public void judge(User user, Category... categories) {
        log.debug("넛지 판정 미구현 — 건너뜁니다 (사용자 {}, 카테고리 {}개)",
                user.getId(), categories.length);
    }
}
