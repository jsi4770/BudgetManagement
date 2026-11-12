package com.back.was.domain.dev;

import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.budget.SpendingFocus;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.dev.dto.SeedRequest;
import com.back.was.domain.dev.dto.SeedResponse;
import com.back.was.domain.nudge.NudgeJudgement;
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

    /**
     * 시연_데이터 4장의 과거 코칭·챌린지 4건은 <b>깔지 않는다</b> — 코칭과 챌린지
     * 도메인이 인수인계되었다(docs/인수인계_넛지_이상치_리포트.md). 되살릴 때 필요한
     * 값(제안 기간 7일 · COACHING 20:00 · CHALLENGE 20:05 · 리포트 다음 날 00:05,
     * 그리고 C1~C4 의 목표·실적·이전 기간 표)은 시연_데이터 4장에 그대로 있고,
     * 지웠을 때의 구현은 {@code git show backup/before-handover:<이 파일>} 에 있다.
     *
     * <p>그래서 지금 DEV-01 은 <b>거래와 예산만</b> 깐다. CHL-01 목록도 ADMIN-01 의
     * KPI(4건 중 3건 = 75%)도 재현되지 않는다.
     */

    private final DemoTransactionPlanner planner;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final NudgeJudgement nudgeJudgement;
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
        // 과거 코칭·챌린지 4건(시연_데이터 4장)이 들어갈 자리다 — 두 도메인이
        // 인수인계되어 비어 있다. 그래서 시드 후에도 CHL-01 목록과 ADMIN-01 의 KPI 는
        // 비어 있다.

        judgeNudgeOnce(user, categories);

        log.info("DEV-01 시드: {} / 거래 {}건", request.scenario(), transactionCount);
        return new SeedResponse(request.scenario(), transactionCount, hasNudge());
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
     * <p><b>지금은 두 테이블만 지운다</b> — 코칭·챌린지·성장 리포트·넛지 테이블이 전부
     * 인수인계와 함께 사라졌다. 돌아오면 아래 순서를 그대로 되살릴 것(자식부터):
     * {@code growth_reports} → {@code challenges} → {@code coaching_messages} →
     * {@code coachings} → {@code transactions} → {@code nudges} → {@code budgets}.
     * {@code CHALLENGE} 와 {@code GROWTH_REPORT} 에는 {@code USER_ID} 가 없어서
     * (ERD 9·10장) 코칭을 경유하는 서브쿼리로 범위를 잡아야 한다 —
     * {@code where coaching_id in (select id from coachings where user_id = :userId)}.
     */
    private void deleteMyData(Long userId) {
        entityManager.flush();

        execute("delete from transactions where user_id = :userId", userId);
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

    /**
     * 응답의 {@code nudgeCreated} — 넛지 테이블과 판정이 인수인계되어 <b>항상
     * {@code false}</b> 다. 필드를 지우지 않은 것은 명세 DEV-01 의 응답 모양이기 때문이다.
     *
     * <p>넛지가 돌아오면 NDG-01 과 <b>같은 조건</b>으로 되살릴 것:
     * {@code findTopByUserIdAndAnalysisStartDateAndStatusOrderByUpdatedAtDesc(
     * userId, Weeks.mondayOf(today), NudgeStatus.UNREAD).isPresent()}.
     */
    private static boolean hasNudge() {
        return false;
    }

    private Map<Long, Category> categoriesById() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, category -> category));
    }

    private void execute(String sql, Long userId) {
        entityManager.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
    }
}
