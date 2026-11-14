package com.back.was.domain.dev;

import com.back.was.domain.ai.CoachingOpeningCommand;
import com.back.was.domain.ai.CoachingOpeningGenerator;
import com.back.was.domain.ai.GrowthFeedbackCommand;
import com.back.was.domain.ai.GrowthFeedbackGenerator;
import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.budget.SpendingFocus;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.challenge.ChallengeRepository;
import com.back.was.domain.challenge.ChallengeStatus;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.CoachingMessage;
import com.back.was.domain.coaching.CoachingMessageRepository;
import com.back.was.domain.coaching.CoachingRepository;
import com.back.was.domain.coaching.GoalType;
import com.back.was.domain.dev.dto.SeedRequest;
import com.back.was.domain.dev.dto.SeedResponse;
import com.back.was.domain.nudge.NudgeJudgement;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.report.GrowthReport;
import com.back.was.domain.report.GrowthReportRepository;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * DEV-01 시연 데이터 초기화 (명세 DEV-01 · 서버_내부_처리 12장 · 시연_데이터 전체).
 *
 * <p>호출한 <b>자신의</b> 데이터를 전부 지우고 시나리오 데이터를 다시 깐다. 생성 규칙은
 * {@link DemoTransactionPlanner} 가 들고 있고(그래서 CSV 와 비교해 검증된다) 이 클래스는
 * <b>영속화와 순서</b>만 책임진다.
 *
 * <p>dev 프로필 전용이다 — 운영에 열리면 누구나 자기 데이터를 날릴 수 있다.
 *
 * <h2>순서가 규칙이다 (서버_내부_처리 12장)</h2>
 * <ol>
 *   <li><b>자식 테이블부터 삭제.</b> FK 가 모두 RESTRICT 라 순서를 틀리면 제약에 걸린다.</li>
 *   <li>가입일을 D − 150일로. 넛지(직전 4주)와 월간 탭(직전 3개월) 비교가 모두 동작하려면
 *       가입일이 그보다 앞서야 한다(시연_데이터 1장).</li>
 *   <li>예산 3건, 거래 일괄, 과거 코칭·챌린지·리포트 4건.</li>
 *   <li><b>넛지 판정을 마지막에 한 번만.</b> 거래마다 돌리면 느리고 중간 상태의 넛지가
 *       생겼다 지워진다(12장).</li>
 * </ol>
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedService {

    /** 시연_데이터 1장: 가입일 = D − 150일. */
    private static final int SIGN_UP_DAYS_AGO = 150;

    /** 시연_데이터 2장: 직전 3개월 예산. 이번 달은 넣지 않는다(자동 복사를 보여 주려고). */
    private static final int BUDGET_MONTHS = 3;
    private static final int MONTHLY_BUDGET = 1_200_000;
    private static final int SAVINGS_GOAL = 200_000;

    /** 시연_데이터 4장: 과거 챌린지의 코칭 제안 기간은 모두 7일. */
    private static final int PROPOSAL_PERIOD_DAYS = 7;

    /** 시연_데이터 4장: COACHING 20:00 → CHALLENGE 20:05 → GROWTH_REPORT 다음 날 00:05. */
    private static final int COACHING_HOUR = 20;
    private static final int CHALLENGE_MINUTE = 5;
    private static final int REPORT_MINUTE = 5;

    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;

    /**
     * 시연_데이터 4장의 완료된 챌린지 4건. <b>실적·이전 기간 값은 3장의 거래로 계산한
     * 값</b>이라 거래 데이터와 일치해야 하고, {@code DemoTransactionPlannerTest} 가 그
     * 일치를 고정한다.
     *
     * <p>상태(SUCCESS·FAILED)를 직접 넣지 않는다 — {@code Challenge.finish()} 가 ERD 9장
     * 규칙으로 판정하게 두면 문서의 결과(C2 만 FAILED)가 그대로 나온다. 직접 넣으면 판정
     * 규칙과 시드가 어긋날 수 있다.
     */
    private static final List<PastChallenge> PAST_CHALLENGES = List.of(
            new PastChallenge(12, "카페 30,000원 이내", CAFE,
                    GoalType.AMOUNT, 30_000, null, 9_000, 2, 15_000, 3),
            new PastChallenge(10, "배달 2회 이하", DELIVERY,
                    GoalType.COUNT, null, 2, 59_000, 3, 60_000, 2),
            new PastChallenge(8, "교통 20,000원 이내", TRANSPORT,
                    GoalType.AMOUNT, 20_000, null, 12_400, 4, 15_500, 5),
            new PastChallenge(6, "배달비 50,000원 이내", DELIVERY,
                    GoalType.AMOUNT, 50_000, null, 35_000, 2, 64_000, 2)
    );

    /** 시연_데이터 4장 표의 한 줄. */
    private record PastChallenge(
            int weeksAgo, String title, long categoryId,
            GoalType goalType, Integer goalAmount, Integer goalCount,
            int spendingAmount, int spendingCount,
            int beforeAmount, int beforeCount
    ) {
    }

    private final DemoTransactionPlanner planner;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final CoachingRepository coachingRepository;
    private final CoachingMessageRepository coachingMessageRepository;
    private final ChallengeRepository challengeRepository;
    private final GrowthReportRepository growthReportRepository;
    private final NudgeRepository nudgeRepository;
    private final NudgeJudgement nudgeJudgement;
    private final CoachingOpeningGenerator coachingOpeningGenerator;
    private final GrowthFeedbackGenerator growthFeedbackGenerator;
    private final EntityManager entityManager;

    /**
     * 전부 지우고 다시 깐다. <b>한 트랜잭션</b>이다 — 중간에 실패하면 반쯤 지워진 상태로
     * 남는 것이 시연 중에 가장 나쁜 결과다.
     *
     * <p>넛지 판정이 같은 트랜잭션에서 돌아도 방금 넣은 거래를 본다. 집계가 JPQL 이라
     * Hibernate 가 flush 를 먼저 하기 때문이고, 거래 후처리가 같은 구조에 기대고 있다.
     *
     * @throws BusinessException 404 {@code NOT_FOUND} — 토큰의 사용자가 없을 때
     */
    @Transactional
    public SeedResponse seed(Long userId, SeedRequest request) {
        LocalDate today = KstTime.today();

        deleteMyData(userId);
        backdateSignUp(userId, today.minusDays(SIGN_UP_DAYS_AGO));

        // 사용자를 '가입일을 밀어 둔 뒤에' 읽는 것이 중요하다. 넛지 판정은 엔티티의
        // createdAt 으로 "4주치 기록이 있는지"를 보므로(ERD 6장 판정 생략 ①), 밀기 전에
        // 읽은 엔티티를 넘기면 가입일이 '지금'이라 판정이 통째로 생략된다 — 그러면
        // DELIVERY_SPIKE 를 깔아도 넛지가 뜨지 않는다.
        User user = userRepository.findById(userId)
                .orElseThrow(BusinessException::notFound);

        Map<Long, Category> categories = categoriesById();
        insertBudgets(user, today);
        int transactionCount = insertTransactions(user, today, request.scenario(), categories);
        insertPastChallenges(user, today, categories);

        judgeNudgeOnce(user, categories);

        log.info("DEV-01 시드: {} / 거래 {}건", request.scenario(), transactionCount);
        return new SeedResponse(request.scenario(), transactionCount, hasNudge(userId, today));
    }

    /**
     * 서버_내부_처리 12장의 삭제 순서 — <b>자식 테이블부터</b>. FK 가 모두 RESTRICT 라
     * 순서를 틀리면 제약 위반이다.
     *
     * <p>네이티브 쿼리를 쓴다. 수백 건을 엔티티로 읽어 지우는 것은 느리고, 삭제가 목적일
     * 때 영속성 컨텍스트에 올릴 이유가 없다. 다만 네이티브 DML 은 컨텍스트를 갱신하지
     * 않으므로 전후로 {@code flush}·{@code clear} 한다 — 안 하면 지워진 행을 가리키는 1차
     * 캐시가 남아 아래 삽입이 이상하게 동작한다.
     *
     * <p>CHALLENGE 와 GROWTH_REPORT 에는 {@code USER_ID} 가 없어서(ERD 9·10장) 코칭을
     * 경유하는 서브쿼리로 범위를 잡는다.
     */
    private void deleteMyData(Long userId) {
        entityManager.flush();

        execute("""
                delete from growth_reports where challenge_id in (
                    select ch.id from challenges ch
                    join coachings c on ch.coaching_id = c.id
                    where c.user_id = :userId)
                """, userId);
        execute("""
                delete from challenges where coaching_id in (
                    select id from coachings where user_id = :userId)
                """, userId);
        execute("""
                delete from coaching_messages where coaching_id in (
                    select id from coachings where user_id = :userId)
                """, userId);
        execute("delete from coachings where user_id = :userId", userId);
        execute("delete from transactions where user_id = :userId", userId);
        execute("delete from nudges where user_id = :userId", userId);
        execute("delete from budgets where user_id = :userId", userId);

        entityManager.clear();
    }

    /**
     * 시연_데이터 1장: 가입일을 D − 150일로.
     *
     * <p>{@code @CreationTimestamp} + {@code updatable = false} 라 JPA 로는 바꿀 수 없다 —
     * 테스트들이 쓰는 네이티브 UPDATE 와 같은 방법이다. 이 값이 넛지의 "판정 생략 ①"과
     * ANL-03 의 {@code dataSufficient} 를 좌우하므로, 틀리면 시연에서 넛지가 아예 뜨지
     * 않는다.
     */
    private void backdateSignUp(Long userId, LocalDate signUpDate) {
        entityManager.createNativeQuery(
                        "update users set created_at = :at where id = :userId")
                .setParameter("at", signUpDate.atStartOfDay())
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.clear();
    }

    /** 시연_데이터 2장: 직전 3개월만. 이번 달은 BUDGET-01 의 자동 복사가 만들게 둔다. */
    private void insertBudgets(User user, LocalDate today) {
        for (int monthsAgo = BUDGET_MONTHS; monthsAgo >= 1; monthsAgo--) {
            budgetRepository.save(Budget.of(
                    user,
                    YearMonth.from(today.minusMonths(monthsAgo)),
                    MONTHLY_BUDGET,
                    SAVINGS_GOAL,
                    SpendingFocus.DELIVERY));
        }
    }

    /**
     * 계획된 거래를 <b>일괄</b> 삽입한다. TX-01 을 한 건씩 부르지 않는 것이 12장의 지시다 —
     * 거래마다 후처리(챌린지 실적·넛지 판정)가 돌면 느리고, 중간 상태의 넛지가 생겼다
     * 지워진다.
     */
    private int insertTransactions(User user, LocalDate today, DemoScenario scenario,
                                   Map<Long, Category> categories) {
        List<Transaction> transactions = planner.plan(today, scenario).stream()
                .map(planned -> Transaction.of(
                        user,
                        categories.get(planned.categoryId()),
                        planned.amount(),
                        planned.date(),
                        planned.memo()))
                .toList();

        transactionRepository.saveAll(transactions);
        return transactions.size();
    }

    /**
     * 시연_데이터 4장: 완료된 코칭 · 챌린지 · 성장 리포트 4건. CHL-01 목록과 ADMIN-01 의
     * KPI(4건 중 3건 성공 = 75%)가 이 데이터를 쓴다.
     *
     * <p><b>진행 중 챌린지는 넣지 않는다</b>(4장) — 시연에서 COACH-04 로 직접 만들어야
     * 하고, 하나라도 있으면 그 확정이 409 로 막힌다.
     */
    private void insertPastChallenges(User user, LocalDate today, Map<Long, Category> categories) {
        LocalDate monday = Weeks.mondayOf(today);

        for (PastChallenge past : PAST_CHALLENGES) {
            LocalDate start = monday.minusWeeks(past.weeksAgo());
            LocalDate end = start.plusDays(6);
            Category category = categories.get(past.categoryId());

            Coaching coaching = coachingRepository.save(Coaching.start(user));
            coaching.propose(category, past.title(), past.goalType(),
                    past.goalAmount(), past.goalCount(), PROPOSAL_PERIOD_DAYS);
            coachingMessageRepository.save(CoachingMessage.ai(coaching,
                    coachingOpeningGenerator.generate(
                            CoachingOpeningCommand.withoutNudge(user.getUsername()))));
            coachingMessageRepository.save(CoachingMessage.user(coaching, "좋아요, 해볼게요"));
            coaching.complete();

            Challenge challenge = challengeRepository.save(Challenge.of(
                    coaching, category, past.title(), past.goalType(),
                    past.goalAmount(), past.goalCount(), start, end));
            challenge.updatePerformance(past.spendingAmount(), past.spendingCount());
            challenge.finish();

            growthReportRepository.save(GrowthReport.of(
                    challenge, past.beforeAmount(), past.beforeCount(),
                    feedbackFor(challenge, past)));

            backdateTimestamps(coaching.getId(), challenge.getId(), start, end);
        }
    }

    private String feedbackFor(Challenge challenge, PastChallenge past) {
        boolean amountGoal = past.goalType() == GoalType.AMOUNT;

        return growthFeedbackGenerator.generate(new GrowthFeedbackCommand(
                challenge.getStatus() == ChallengeStatus.SUCCESS,
                past.goalType(),
                past.goalType().requiredGoal(past.goalAmount(), past.goalCount()),
                amountGoal ? past.spendingAmount() : past.spendingCount(),
                past.beforeAmount(),
                PROPOSAL_PERIOD_DAYS,
                challenge.getCategory().getName()));
    }

    /**
     * 시연_데이터 4장의 시각들을 박는다: 코칭 시작일 20:00, 챌린지 20:05, 리포트는 종료일
     * 다음 날 00:05(스케줄러가 돌았을 시각).
     *
     * <p>세 값 모두 {@code @CreationTimestamp} 라 네이티브 UPDATE 가 필요하다. 이게 없으면
     * 네 챌린지의 {@code createdAt} 이 전부 "지금"이 되어 CHL-01 목록의 순서가 시연 흐름과
     * 어긋나고(최신 확정 순 정렬), 리포트의 생성 시각도 과거 챌린지의 것으로 보이지 않는다.
     */
    private void backdateTimestamps(Long coachingId, Long challengeId,
                                    LocalDate start, LocalDate end) {
        entityManager.flush();

        updateCreatedAt("coachings", coachingId, start.atTime(COACHING_HOUR, 0));
        updateCreatedAt("challenges", challengeId, start.atTime(COACHING_HOUR, CHALLENGE_MINUTE));
        entityManager.createNativeQuery(
                        "update growth_reports set created_at = :at where challenge_id = :id")
                .setParameter("at", end.plusDays(1).atTime(0, REPORT_MINUTE))
                .setParameter("id", challengeId)
                .executeUpdate();
    }

    private void updateCreatedAt(String table, Long id, LocalDateTime at) {
        // 테이블 이름은 이 클래스의 상수에서만 오므로 주입 경로가 없다.
        entityManager.createNativeQuery(
                        "update " + table + " set created_at = :at where id = :id")
                .setParameter("at", at)
                .setParameter("id", id)
                .executeUpdate();
    }

    /**
     * 서버_내부_처리 12장: "이번 주 넛지 판정 <b>1회</b> 실행".
     *
     * <p>지출 카테고리 전부를 넘긴다 — 어느 카테고리가 조건을 넘는지는 판정이 정하고
     * (월세·수입은 {@code NudgeJudgement} 가 스스로 걸러낸다), 표본 조회는 카테고리 수와
     * 무관하게 구간 수만큼이라 한 번에 넘기는 쪽이 싸다.
     */
    private void judgeNudgeOnce(User user, Map<Long, Category> categories) {
        Category[] expenses = categories.values().stream()
                .filter(category -> category.getType() == CategoryType.EXPENSE)
                .toArray(Category[]::new);

        nudgeJudgement.judge(user, expenses);
    }

    /** 응답의 {@code nudgeCreated} — NDG-01 과 같은 조건으로 본다. */
    private boolean hasNudge(Long userId, LocalDate today) {
        return nudgeRepository
                .findTopByUserIdAndAnalysisStartDateAndStatusOrderByUpdatedAtDesc(
                        userId, Weeks.mondayOf(today), NudgeStatus.UNREAD)
                .isPresent();
    }

    private Map<Long, Category> categoriesById() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, category -> category));
    }

    private void execute(String sql, Long userId) {
        entityManager.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
    }
}
