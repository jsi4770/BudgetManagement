package com.back.was.domain.dev;

import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeService;
import com.back.was.domain.challenge.ChallengeTermination;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.dev.dto.CompleteChallengeRequest;
import com.back.was.domain.report.dto.GrowthReportResponse;
import com.back.was.domain.transaction.TransactionService;
import com.back.was.domain.transaction.dto.CreateTransactionRequest;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.support.KstTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * DEV-02 챌린지 강제 종료 (명세 DEV-02 · 서버_내부_처리 12장).
 *
 * <p>시연에서 종료일을 기다리지 않고 "기간 종료 · 결과 보기" / "달성 시나리오" /
 * "미달성 시나리오" 세 버튼을 누르기 위한 도구다. 그래서 <b>dev 프로필에서만</b> 빈이
 * 등록된다(CLAUDE.md 규칙) — 운영에 올라가면 누구나 자기 챌린지를 즉시 종료시킬 수 있다.
 *
 * <p><b>판정 로직을 다시 쓰지 않는다.</b> {@link ChallengeTermination#terminate} 를 그대로
 * 부른다(서버_내부_처리 4장 마지막 줄: "DEV-02는 이 판정 로직을 종료일 대기 없이 즉시
 * 호출"). 여기서 따로 구현하면 시연에서 본 결과와 실제 스케줄러의 결과가 달라질 수 있다.
 * {@code terminate} 가 종료일을 보지 않는 것도 그래서다 — 날짜 조건은 일일 작업의 조회에만
 * 있다.
 *
 * <p>거래를 추가하는 것도 <b>TX-01 경로</b>({@link TransactionService#create})를 쓴다.
 * 리포지터리에 직접 넣으면 거래 후처리 2번이 돌지 않아 챌린지의 실적이 갱신되지 않고,
 * 그러면 판정이 옛 숫자를 읽는다.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevChallengeService {

    /**
     * 달성 시나리오가 맞출 목표 대비 비율. 시연_데이터 5장 8단계를 재현한다:
     * 목표 50,000원 → 35,000원(70%)이 되도록 11,000원을 추가.
     *
     * <p>명세는 "목표 이하로 맞춘다"까지만 정하고 정확한 비율은 예시로만 보여 준다.
     * 70% 는 그 예시에서 역산한 값이다.
     */
    private static final int SUCCESS_PERCENT = 70;

    /**
     * 미달성 시나리오가 맞출 비율. 같은 단계의 FAILED 예시 재현:
     * 목표 50,000원 → 56,000원(112%)이 되도록 32,000원을 추가.
     */
    private static final int FAILED_PERCENT = 112;

    /** 횟수 목표에서 추가할 거래 한 건의 금액. 횟수만 중요해서 값은 상징적이다. */
    private static final int COUNT_SCENARIO_AMOUNT = 10_000;

    private final ChallengeRepository challengeRepository;
    private final ChallengeTermination challengeTermination;
    private final ChallengeService challengeService;
    private final TransactionService transactionService;

    /**
     * 시나리오대로 거래를 보태고 즉시 판정한 뒤 성장 리포트를 돌려준다. 응답은 CHL-03 과
     * 같다(명세 DEV-02).
     *
     * <p><b>이 메서드에 {@code @Transactional} 이 없는 것이 중요하다.</b>
     * {@code terminate} 가 {@code REQUIRES_NEW} 로 <b>새 트랜잭션</b>을 열기 때문에, 여기서
     * 트랜잭션을 쥐고 있으면 방금 추가한 거래가 아직 커밋되지 않아 판정이 그것을 보지
     * 못한다. 세 단계가 각자 커밋한다 — {@code ChallengeTerminationJob.terminateDue} 가
     * 트랜잭션을 갖지 않는 것과 같은 이유다.
     *
     * <p>이미 종료된 챌린지면 거래만 추가되고 판정은 건너뛴다({@code terminate} 가 그렇게
     * 막는다) — "기간 종료 · 결과 보기"를 두 번 눌러도 결과가 바뀌지 않는다는 뜻이고,
     * ERD 9장의 "늦은 기록은 반영하지 않아요"와 일치한다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 없는/남의 챌린지
     */
    public GrowthReportResponse complete(Long userId, Long challengeId,
                                         CompleteChallengeRequest request) {
        Challenge challenge = challengeRepository.findByIdAndCoachingUserId(challengeId, userId)
                .orElseThrow(BusinessException::notFound);

        if (request.scenario() != null && !challenge.isFinished()) {
            addScenarioTransactions(userId, challenge, request.scenario());
        }
        challengeTermination.terminate(challengeId);

        return challengeService.getReport(userId, challengeId);
    }

    /**
     * 명세 DEV-02 표의 "오늘 날짜로 거래 추가".
     *
     * <p>날짜가 <b>오늘</b>인 것은 명세의 지정이고, 그래서 시나리오가 실적을 움직이려면
     * 오늘이 챌린지 기간 안이어야 한다 — DEV-02 의 목적 자체가 "종료일을 기다리지 않는"
     * 것이므로 그 전제는 항상 참이다.
     *
     * <p>목표 유형에 따라 바꿀 값이 다르다: {@code AMOUNT} 는 금액 한 건으로 맞추고,
     * {@code COUNT} 는 건수를 맞춰야 하므로 여러 건을 넣는다.
     */
    private void addScenarioTransactions(Long userId, Challenge challenge,
                                         DevChallengeScenario scenario) {
        if (challenge.getGoalType() == GoalType.AMOUNT) {
            addAmountScenario(userId, challenge, scenario);
        } else {
            addCountScenario(userId, challenge, scenario);
        }
    }

    /**
     * 금액 목표 — 목표의 {@value #SUCCESS_PERCENT}% / {@value #FAILED_PERCENT}% 가 되도록
     * 차액 한 건을 넣는다.
     *
     * <p>차액이 0 이하면 아무것도 넣지 않는다. 이미 목표를 넘긴 상태에서 "달성 시나리오"를
     * 누른 경우가 그런데, <b>거래를 추가하는 방식으로는 지출을 줄일 수 없다</b> — 그때는
     * 실제 기록대로 판정된다(즉 {@code FAILED}). 시연 흐름에서는 확정 직후라 항상 목표
     * 아래에서 시작하므로 생기지 않는다.
     */
    private void addAmountScenario(Long userId, Challenge challenge,
                                   DevChallengeScenario scenario) {
        int goal = challenge.getGoalAmount();
        int percent = scenario == DevChallengeScenario.SUCCESS ? SUCCESS_PERCENT : FAILED_PERCENT;
        int needed = goal * percent / 100 - challenge.getSpendingAmount();

        if (needed <= 0) {
            log.warn("챌린지 {} {} 시나리오: 추가할 금액이 없어 현재 기록대로 판정한다 (실적 {}원, 목표 {}원)",
                    challenge.getId(), scenario, challenge.getSpendingAmount(), goal);
            return;
        }
        addTransaction(userId, challenge, needed);
    }

    /**
     * 횟수 목표 — {@code FAILED} 는 목표를 한 건 넘기도록 건수를 채운다.
     *
     * <p>{@code SUCCESS} 는 아무것도 넣지 않는다. 거래를 추가하면 건수가 <b>늘어나기만</b>
     * 하므로 달성에 도움이 되지 않는다 — 이미 목표 이하면 그대로 {@code SUCCESS} 이고,
     * 넘겼다면 추가로는 되돌릴 수 없다.
     *
     * <p>시연은 금액 목표만 쓰므로(시연_데이터 5장 8단계) 이쪽은 같은 규칙을 횟수로 옮긴
     * 일반화다.
     */
    private void addCountScenario(Long userId, Challenge challenge,
                                  DevChallengeScenario scenario) {
        if (scenario == DevChallengeScenario.SUCCESS) {
            log.warn("챌린지 {} SUCCESS 시나리오: 횟수 목표는 거래 추가로 맞출 수 없어 현재 기록대로 판정한다",
                    challenge.getId());
            return;
        }

        int needed = challenge.getGoalCount() + 1 - challenge.getSpendingCount();
        for (int i = 0; i < needed; i++) {
            addTransaction(userId, challenge, COUNT_SCENARIO_AMOUNT);
        }
    }

    /**
     * TX-01 로 거래를 넣는다 — 후처리 2·3번이 함께 돌아 챌린지 실적이 즉시 갱신된다.
     *
     * <p>메모에 표식을 남긴다. 시연 중에 생긴 데이터를 눈으로 구분할 수 있어야 하고,
     * DEV-01 로 초기화하기 전에 어떤 거래가 버튼으로 들어온 것인지 알 수 있다.
     */
    private void addTransaction(Long userId, Challenge challenge, int amount) {
        transactionService.create(userId, new CreateTransactionRequest(
                challenge.getCategory().getId(),
                amount,
                KstTime.today(),
                "시연용 자동 기록 (DEV-02)"));
    }
}
