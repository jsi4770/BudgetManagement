package com.back.was.domain.nudge;

import com.back.was.domain.ai.NudgeText;
import com.back.was.domain.ai.NudgeTextCommand;
import com.back.was.domain.ai.NudgeTextGenerator;
import com.back.was.domain.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 거래 변경 후처리 <b>5번</b>: 넛지 AI 문구 생성 (서버_내부_처리 1장 · 8장).
 *
 * <p>앞의 2·3번과 달리 <b>커밋 후 비동기</b>다. 문구 생성은 외부 호출이라 느릴 수 있고,
 * 실패해도 거래 저장을 되돌릴 이유가 없다 — 카드는 문구 없이도 성립한다(NDG-02 가 그
 * 상태를 "AI가 분석 중이에요"로 보여 준다).
 *
 * <p>그래서 {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async} 조합이다
 * (8장이 지정한 그대로). 이 메서드는 커밋 후 <b>다른 스레드</b>에서 시작되어 활성
 * 트랜잭션이 없으므로 DB 작업은 새 트랜잭션에서 한다. <b>전파는 {@code REQUIRES_NEW} 여야
 * 한다</b> — {@code AFTER_COMMIT} 시점에는 끝난 트랜잭션의 자원이 아직 묶여 있을 수 있어서
 * ({@code @Async} 가 꺼진 테스트처럼 같은 스레드에서 돌 때) {@code REQUIRED} 로는 거기 참여해
 * 쓰기가 조용히 버려진다. Spring 이 이 리스너의 {@code @Transactional(REQUIRED)} 를 기동
 * 시점에 거부하는 것도 같은 이유다.
 *
 * <p><b>메서드에 {@code @Transactional} 을 두지 않고 트랜잭션을 둘로 나눈다</b> (2026-11-15):
 * 읽기 → 트랜잭션 밖 Gemini 호출 → 쓰기. 한 트랜잭션이면 비동기 스레드마다(기본 8개) DB
 * 커넥션을 쥐고 Gemini 를 기다려서, 거래 등록이 몰릴 때 커넥션 풀(기본 10개)이 말랐다.
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
    private final PlatformTransactionManager transactionManager;

    /**
     * <p>넛지가 이미 사라졌으면 아무것도 하지 않는다. 커밋과 이 호출 사이에 사용자가 거래를
     * 지워 조건이 꺾였을 수 있다 — 그때 넛지는 삭제되고, 지워진 카드에 문구를 채울 이유는
     * 없다.
     *
     * <p>예외를 삼키지 않지만 비동기라 호출자에게 전파되지도 않는다. 문구가 없는 카드는
     * 유효한 상태이므로 로그만 남기고 끝낸다 — 재시도 큐를 두는 건 과하다(다음 거래 변경
     * 때 어차피 다시 판정·생성된다).
     *
     * <p><b>저장 직전에 입력값을 다시 만들어 비교한다.</b> Gemini 를 기다리는 사이 거래가 또
     * 바뀌어 넛지 금액이 갱신됐으면, 그 갱신이 쏜 이벤트가 새 문구를 만들고 있다 — 여기서 옛
     * 숫자로 만든 문구를 저장하면 (늦게 끝난 쪽이 이기므로) 카드 숫자와 문구가 어긋날 수 있다.
     * 그래서 다르면 저장하지 않는다. 넛지가 지워졌어도 마찬가지다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void fill(NudgeJudgedEvent event) {
        Long nudgeId = event.nudgeId();
        NudgeTextCommand command = inNewTransaction(() ->
                nudgeRepository.findById(nudgeId).map(this::commandOf).orElse(null));
        if (command == null) {
            return;
        }

        NudgeText text = nudgeTextGenerator.generate(command);

        inNewTransaction(() -> {
            nudgeRepository.findById(nudgeId).ifPresent(nudge -> {
                if (!commandOf(nudge).equals(command)) {
                    log.debug("넛지 {} 가 문구를 만드는 사이 갱신돼 이 문구는 버린다", nudgeId);
                    return;
                }
                nudge.fillAiText(text.analysis(), text.recommendation());
                log.debug("넛지 {} 문구 생성", nudgeId);
            });
            return null;
        });
    }

    private <T> T inNewTransaction(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
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
