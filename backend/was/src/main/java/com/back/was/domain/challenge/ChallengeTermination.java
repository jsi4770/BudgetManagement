package com.back.was.domain.challenge;

import com.back.was.domain.ai.GrowthFeedbackCommand;
import com.back.was.domain.ai.GrowthFeedbackGenerator;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.report.GrowthReport;
import com.back.was.domain.report.GrowthReportRepository;
import com.back.was.domain.transaction.SpendingTotal;
import com.back.was.domain.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 챌린지 종료 판정 + 성장 리포트 생성 (서버_내부_처리 4장 · ERD 9·10장).
 *
 * <p><b>한 챌린지 = 한 트랜잭션</b>이고, 여러 건을 도는 쪽은 일부러 다른 빈에 있다
 * ({@link ChallengeTerminationJob}). 같은 빈에서 반복문이 이 메서드를 부르면 프록시를
 * 거치지 않아 {@code @Transactional} 이 <b>조용히 무시된다</b> — 그러면 열 건이 한
 * 트랜잭션에 묶이고, 아홉 번째 실패가 앞의 여덟 건을 되돌린다.
 *
 * <p>DEV-02 는 종료일을 기다리지 않고 {@link #terminate} 를 직접 부르게 될 자리다
 * (서버_내부_처리 4장 마지막 줄). 그래서 판정 로직이 스케줄러 쪽에 없다.
 *
 * <p><b>판정은 되돌릴 수 없다.</b> {@code Challenge.finish()} 가 이미 종료된 챌린지를
 * 거부하고, 리포트도 {@code CHALLENGE_ID} UNIQUE 로 한 번만 생긴다 — 그 둘이 "늦은 기록은
 * 반영하지 않아요"(ERD 9장)를 구조적으로 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeTermination {

    private final ChallengeRepository challengeRepository;
    private final GrowthReportRepository growthReportRepository;
    private final TransactionRepository transactionRepository;
    private final GrowthFeedbackGenerator growthFeedbackGenerator;

    /**
     * 챌린지 하나를 판정하고 리포트를 만든다.
     *
     * <p><b>{@code REQUIRES_NEW} 로 건마다 독립 트랜잭션</b>이다. 판정은 되돌릴 수 없는
     * 전이라서, 열 건 중 아홉 번째가 터졌을 때 앞의 여덟 건까지 롤백되면 다음 실행에서
     * 다시 판정해야 하고 그 사이에 들어온 거래가 결과를 바꾼다(ERD 9장이 금지하는 "늦은
     * 기록 반영"이다). DEV-02 가 트랜잭션 안에서 부를 때도 같은 이유로 분리돼야 한다.
     *
     * <p><b>엔티티가 아니라 ID 를 받는다.</b> 새 트랜잭션에서는 어차피 다시 읽어야 하고,
     * 목록 조회와 이 호출 사이에 다른 요청이 같은 챌린지를 건드렸을 수도 있다.
     *
     * <p>순서가 규칙이다: <b>{@code BEFORE_} 집계 → 판정 → 리포트</b>. 집계를 판정보다
     * 먼저 하는 건 읽기라 순서가 자유롭지만, 리포트는 종료된 챌린지만 받으므로
     * ({@code GrowthReport.of}) 판정 뒤여야 한다.
     *
     * <p>이미 리포트가 있으면 아무것도 하지 않는다 — 판정이 두 번 돌 경우의 방어이고,
     * {@code CHALLENGE_ID} UNIQUE 로 터뜨리는 것보다 조용히 건너뛰는 쪽이 스케줄러의 나머지
     * 대상을 지킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void terminate(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null || challenge.isFinished()
                || growthReportRepository.existsByChallengeId(challengeId)) {
            return;
        }

        SpendingTotal before = sumBeforePeriod(challenge);
        challenge.finish();
        growthReportRepository.save(GrowthReport.of(
                challenge,
                Math.toIntExact(before.amount()),
                Math.toIntExact(before.count()),
                feedbackFor(challenge, before)));

        log.debug("챌린지 {} 판정: {}", challengeId, challenge.getStatus());
    }

    /** 챌린지 기간(일) — 양끝 포함. COACH-04 가 7·14·30 중 하나로 확정한다. */
    static long periodDaysOf(Challenge challenge) {
        return challenge.getEndDate().toEpochDay() - challenge.getStartDate().toEpochDay() + 1;
    }

    /**
     * 챌린지 시작일 <b>직전, 같은 길이 기간</b>의 같은 카테고리 지출
     * (ERD 10장 · 서버_내부_처리 4장).
     *
     * <p>명세의 예시가 정의다: 7일 챌린지 11-04 ~ 11-10 → <b>10-28 ~ 11-03</b>. 즉
     * {@code [startDate − 기간, startDate − 1]} 이고 양끝을 포함한다.
     *
     * <p>이 값을 리포트에 <b>박아 두는</b> 이유는 {@link GrowthReport} 설명에 있다 — 나중에
     * 다시 집계하면 과거 거래가 수정될 때 "챌린지 전 금액"이 조용히 달라진다.
     */
    private SpendingTotal sumBeforePeriod(Challenge challenge) {
        long periodDays = periodDaysOf(challenge);
        LocalDate beforeEnd = challenge.getStartDate().minusDays(1);
        LocalDate beforeStart = challenge.getStartDate().minusDays(periodDays);

        return transactionRepository.sumCategorySpending(
                challenge.getCoaching().getUser().getId(),
                challenge.getCategory().getId(),
                beforeStart,
                beforeEnd);
    }

    /**
     * {@code docs/AI_대체_문구.md} 7장의 변수를 모아 포트에 넘긴다.
     *
     * <p>목표와 실적은 {@code goalType} 이 고르는 짝이다 — {@code Challenge.finish()} 가
     * 판정에 쓴 것과 같은 짝이어야 문구의 "{@code {gap}}원 덜 썼어요"가 판정 결과와 맞는다.
     *
     * <p>포트는 실패하지 않으므로({@link GrowthFeedbackGenerator}) {@code try/catch} 가
     * 없다. Gemini 가 없으면 7장 템플릿이 들어온다.
     */
    private String feedbackFor(Challenge challenge, SpendingTotal before) {
        boolean amountGoal = challenge.getGoalType() == GoalType.AMOUNT;
        int goal = challenge.getGoalType()
                .requiredGoal(challenge.getGoalAmount(), challenge.getGoalCount());
        int spending = amountGoal ? challenge.getSpendingAmount() : challenge.getSpendingCount();

        return growthFeedbackGenerator.generate(new GrowthFeedbackCommand(
                challenge.getStatus() == ChallengeStatus.SUCCESS,
                challenge.getGoalType(),
                goal,
                spending,
                Math.toIntExact(before.amount()),
                (int) periodDaysOf(challenge)));
    }

}
