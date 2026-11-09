package com.back.was.domain.coaching;

import com.back.was.domain.ai.ProposedGoal;
import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.budget.SpendingFocus;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.nudge.Nudge;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.transaction.Transaction;
import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.back.was.global.support.YearMonths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙 기반 목표 제안 (docs/AI_대체_문구.md 5장).
 *
 * <p>CLAUDE.md 가 요구하는 단위 테스트 중 하나다 — Gemini 가 없는 동안 COACH-03 의 모든
 * 제안이 여기서 나오므로, 카테고리 선택 ①②③ 의 <b>우선순위</b>와 금액 공식을 못박는다.
 *
 * <p>금액 기대값은 문서의 예시 그대로다: 직전 4주 주간 평균 60,000 → 80% = 48,000 →
 * 만 원 단위 내림 = <b>40,000</b>. 4주 합계 240,000원을 넣어 그 평균을 만든다.
 */
@SpringBootTest
@Transactional
class RuleBasedGoalProposerTest {

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;

    private static final LocalDate TODAY = KstTime.today();

    /** 직전 4주 전체 (월 ~ 지난 일요일). 거래는 이 안에 넣어야 집계된다. */
    private static final DateRange LAST_FOUR_WEEKS = Weeks.fullWeeksBefore(TODAY, 4);

    @Autowired
    RuleBasedGoalProposer proposer;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    NudgeRepository nudgeRepository;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.save(User.signUp("홍길동", "proposer@email.com", "irrelevant-hash"));
    }

    private Category category(long id) {
        return categoryRepository.findById(id).orElseThrow();
    }

    /** 직전 4주 구간 안에 거래 한 건. 날짜는 구간 시작일로 고정한다(어느 날이든 합계는 같다). */
    private void givenSpending(long categoryId, int amount) {
        transactionRepository.save(Transaction.of(
                user, category(categoryId), amount, LAST_FOUR_WEEKS.start(), null));
    }

    private void givenCoachingStartedNudge(long categoryId) {
        Nudge nudge = nudgeRepository.save(Nudge.of(
                user, category(categoryId), Weeks.elapsedWeekOf(TODAY),
                SpendingComparison.of(101_000, List.of(55_000, 62_000, 58_000, 65_000))));
        nudge.changeStatus(NudgeStatus.COACHING_STARTED);
    }

    private void givenSpendingFocus(SpendingFocus focus) {
        budgetRepository.save(Budget.of(user, YearMonths.current(), 1_000_000, 0, focus));
    }

    // --- 카테고리 선택 우선순위 (5장 ①②③) ---------------------------------

    @Nested
    @DisplayName("카테고리 선택")
    class CategoryChoice {

        @Test
        @DisplayName("① 이번 주 COACHING_STARTED 넛지가 있으면 그 카테고리 — 지출이 더 큰 쪽을 이긴다")
        void nudgeCategoryWinsFirst() {
            givenSpending(FOOD, 1_000_000);   // ③ 이라면 식비가 뽑힐 금액
            givenSpendingFocus(SpendingFocus.DELIVERY);  // ② 라면 배달
            givenCoachingStartedNudge(CAFE);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::categoryId)
                    .isEqualTo(CAFE);
        }

        @Test
        @DisplayName("② 넛지가 없으면 소비 관리 목표 — DELIVERY → 배달, CAFE → 카페")
        void spendingFocusComesSecond() {
            givenSpending(FOOD, 1_000_000);
            givenSpendingFocus(SpendingFocus.DELIVERY);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::categoryId)
                    .isEqualTo(DELIVERY);
        }

        @Test
        @DisplayName("② FIXED_COST 는 카테고리로 번역되지 않아 ③ 으로 넘어간다 (5장 문구)")
        void fixedCostFallsThroughToSpending() {
            givenSpending(FOOD, 1_000_000);
            givenSpendingFocus(SpendingFocus.FIXED_COST);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::categoryId)
                    .isEqualTo(FOOD);
        }

        @Test
        @DisplayName("③ 목표도 없으면 직전 4주 지출이 가장 큰 카테고리")
        void biggestSpendingComesLast() {
            givenSpending(FOOD, 200_000);
            givenSpending(DELIVERY, 300_000);
            givenSpending(CAFE, 100_000);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::categoryId)
                    .isEqualTo(DELIVERY);
        }

        @Test
        @DisplayName("③ 월세는 아무리 커도 뽑히지 않는다 (ERD 4장)")
        void rentIsNeverPicked() {
            givenSpending(RentCategory.ID, 9_000_000);
            givenSpending(CAFE, 100_000);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::categoryId)
                    .isEqualTo(CAFE);
        }

        @Test
        @DisplayName("이번 주 지출은 기준에 들어가지 않는다 — 직전 4주는 끝난 주들이다")
        void thisWeeksSpendingIsNotCounted() {
            transactionRepository.save(Transaction.of(user, category(CAFE), 500_000, TODAY, null));

            // 이번 주 거래만 있으면 직전 4주 집계가 비어 ③ 이 아무것도 못 고른다
            assertThat(proposer.propose(user)).isEmpty();
        }

        @Test
        @DisplayName("고를 근거가 하나도 없으면 제안하지 않는다 — 아무 카테고리나 찍지 않는다")
        void proposesNothingWithoutAnySignal() {
            assertThat(proposer.propose(user)).isEmpty();
        }
    }

    // --- 목표 금액 공식 (5장) ------------------------------------------------

    @Nested
    @DisplayName("목표 금액")
    class GoalAmount {

        @Test
        @DisplayName("문서의 예시: 주간 평균 60,000 → 80% 48,000 → 만 원 단위 내림 40,000")
        void followsTheDocumentExample() {
            givenSpending(DELIVERY, 240_000);   // 4주 합계 → 주간 평균 60,000
            givenSpendingFocus(SpendingFocus.DELIVERY);

            assertThat(proposer.propose(user)).get().satisfies(goal -> {
                assertThat(goal.goalType()).isEqualTo(GoalType.AMOUNT);
                assertThat(goal.goalAmount()).isEqualTo(40_000);
                assertThat(goal.goalCount()).isNull();
                assertThat(goal.periodDays()).isEqualTo(7);
                // 제목은 6장 템플릿
                assertThat(goal.title()).isEqualTo("배달 40,000원 이내");
            });
        }

        @Test
        @DisplayName("반올림이 아니라 내림이다 — 주간 평균 124,999 → 99,999 → 90,000")
        void floorsToTenThousand() {
            givenSpending(DELIVERY, 499_996);   // 주간 평균 124,999
            givenSpendingFocus(SpendingFocus.DELIVERY);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::goalAmount)
                    .isEqualTo(90_000);
        }

        @Test
        @DisplayName("지출이 적어도 최소 10,000원 — 0원 목표는 지킬 수 없는 목표다")
        void neverGoesBelowTheMinimum() {
            givenSpending(DELIVERY, 4_000);     // 80% 를 내리면 0 이 된다
            givenSpendingFocus(SpendingFocus.DELIVERY);

            assertThat(proposer.propose(user))
                    .get()
                    .extracting(ProposedGoal::goalAmount)
                    .isEqualTo(10_000);
        }

        @Test
        @DisplayName("①·② 로 고른 카테고리에 지출 기록이 없어도 최소 금액으로 제안한다")
        void proposesMinimumForACategoryWithoutHistory() {
            givenCoachingStartedNudge(CAFE);

            Optional<ProposedGoal> goal = proposer.propose(user);

            assertThat(goal).get().extracting(ProposedGoal::categoryId).isEqualTo(CAFE);
            assertThat(goal).get().extracting(ProposedGoal::goalAmount).isEqualTo(10_000);
        }
    }
}
