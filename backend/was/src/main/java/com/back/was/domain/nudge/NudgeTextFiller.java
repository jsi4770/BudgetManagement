package com.back.was.domain.nudge;

import com.back.was.domain.ai.NudgeText;
import com.back.was.domain.ai.NudgeTextCommand;
import com.back.was.domain.ai.NudgeTextGenerator;
import com.back.was.domain.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 거래 변경 후처리 <b>5번</b>: 넛지 AI 문구 생성 (서버_내부_처리 1장 · 8장).
 *
 * <p>앞의 2·3번과 달리 <b>커밋 후 비동기</b>다. 문구 생성은 외부 호출이라 느릴 수 있고,
 * 실패해도 거래 저장을 되돌릴 이유가 없다 — 카드는 문구 없이도 성립한다(NDG-02 가 그
 * 상태를 "AI가 분석 중이에요"로 보여 준다).
 *
 * <p>그래서 {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async} 조합이다
 * (8장이 지정한 그대로). {@code @Transactional} 을 다시 붙이는 건 이 메서드가 커밋 후
 * <b>다른 스레드</b>에서 시작되어 활성 트랜잭션이 없기 때문이고, <b>전파가
 * {@code REQUIRES_NEW} 여야 한다</b> — Spring 은 {@code AFTER_COMMIT} 리스너에 기본 전파
 * ({@code REQUIRED}) 의 {@code @Transactional} 이 붙어 있으면 <b>기동 시점에</b>
 * {@code IllegalStateException} 을 던진다. 이미 끝난 트랜잭션에 참여하려 드는 설정이라
 * 쓰기가 조용히 버려지기 때문이다.
 *
 * <p>금액이 갱신되면 판정이 이벤트를 다시 쏘므로 문구도 다시 생성된다. 새 문구가 저장될
 * 때까지는 이전 문구가 남는다 — {@code Nudge.fillAiText} 가 덮어쓰기만 하기 때문이다
 * (ERD 6장 갱신 규칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NudgeTextFiller {

    private final NudgeRepository nudgeRepository;
    private final TransactionRepository transactionRepository;
    private final NudgeTextGenerator nudgeTextGenerator;

    /**
     * <p>넛지가 이미 사라졌으면 아무것도 하지 않는다. 커밋과 이 호출 사이에 사용자가 거래를
     * 지워 조건이 꺾였을 수 있다 — 그때 넛지는 삭제되고, 지워진 카드에 문구를 채울 이유는
     * 없다.
     *
     * <p>예외를 삼키지 않지만 비동기라 호출자에게 전파되지도 않는다. 문구가 없는 카드는
     * 유효한 상태이므로 로그만 남기고 끝낸다 — 재시도 큐를 두는 건 과하다(다음 거래 변경
     * 때 어차피 다시 판정·생성된다).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void fill(NudgeJudgedEvent event) {
        nudgeRepository.findById(event.nudgeId()).ifPresent(nudge -> {
            NudgeText text = nudgeTextGenerator.generate(commandOf(nudge));
            nudge.fillAiText(text.analysis(), text.recommendation());
            log.debug("넛지 {} 문구 생성", nudge.getId());
        });
    }

    /**
     * {@code {days}} 는 저장된 분석 기간의 길이로 센다 — "오늘"을 다시 묻지 않는다. 비동기
     * 실행이 자정을 넘겼더라도 문구는 판정된 기간을 설명해야 한다.
     *
     * <p>{@code {count}} 만 추가 쿼리가 필요하다. 넛지에는 거래 <b>횟수</b>가 없는데(ERD
     * 6장은 금액만 저장한다) 템플릿 2번이 "{count}번 결제했어요"를 쓴다.
     */
    private NudgeTextCommand commandOf(Nudge nudge) {
        int elapsedDays = (int) (nudge.getAnalysisEndDate().toEpochDay()
                - nudge.getAnalysisStartDate().toEpochDay()) + 1;
        long count = transactionRepository.countByUserIdAndCategoryIdAndTransactionDateBetween(
                nudge.getUser().getId(),
                nudge.getCategory().getId(),
                nudge.getAnalysisStartDate(),
                nudge.getAnalysisEndDate());

        return new NudgeTextCommand(
                nudge.getCategory().getName(),
                nudge.getSpendingAmount(),
                nudge.getAvgAmount(),
                nudge.getGrowthRate(),
                elapsedDays,
                (int) count);
    }
}
