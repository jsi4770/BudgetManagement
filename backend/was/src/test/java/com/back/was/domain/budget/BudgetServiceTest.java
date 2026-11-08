package com.back.was.domain.budget;

import com.back.was.domain.budget.dto.BudgetResponse;
import com.back.was.domain.budget.dto.SaveBudgetRequest;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.support.YearMonths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예산 자동 복사 규칙(ERD 3장 · 서버_내부_처리 5장) 단위 테스트.
 *
 * <p>기대값은 docs/시연_데이터.md 2장 — 3개월 전부터 지난 달까지 1,200,000원 / 저축
 * 200,000원 / DELIVERY 이고 <b>이번 달 행은 없다</b>. 홈 화면 첫 조회에서 복사가 돌아야
 * 한다.
 */
@SpringBootTest
@Transactional
class BudgetServiceTest {

    private static final YearMonth THIS_MONTH = YearMonths.current();

    @Autowired
    BudgetService budgetService;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void createUser() {
        User user = userRepository.save(
                User.signUp("홍길동", "copy@email.com", "irrelevant-hash"));
        userId = user.getId();
    }

    private void givenBudget(YearMonth yearMonth, int monthlyBudget, int savingsGoal, SpendingFocus focus) {
        budgetService.saveBudget(userId, yearMonth,
                new SaveBudgetRequest(monthlyBudget, savingsGoal, focus));
    }

    @Test
    @DisplayName("시연 데이터 2장: 8·9·10월만 있을 때 11월 조회 → 10월 값을 복사한 11월 행이 생긴다")
    void copiesLatestOfThreePastMonths() {
        givenBudget(THIS_MONTH.minusMonths(3), 1_200_000, 200_000, SpendingFocus.DELIVERY);
        givenBudget(THIS_MONTH.minusMonths(2), 1_200_000, 200_000, SpendingFocus.DELIVERY);
        givenBudget(THIS_MONTH.minusMonths(1), 1_200_000, 200_000, SpendingFocus.DELIVERY);

        BudgetResponse response = budgetService.getBudget(userId, THIS_MONTH);

        assertThat(response).isEqualTo(new BudgetResponse(
                THIS_MONTH.toString(), 1_200_000, 200_000, "DELIVERY"));
        assertThat(budgetRepository.findByUserIdAndYearMonth(userId, THIS_MONTH.toString()))
                .isPresent();
    }

    @Test
    @DisplayName("원본은 '지난 달'이 아니라 '가장 최근 월' — 값이 다르면 최근 달 값이 복사된다")
    void copiesTheMostRecentValues() {
        givenBudget(THIS_MONTH.minusMonths(5), 500_000, 0, SpendingFocus.CAFE);
        givenBudget(THIS_MONTH.minusMonths(2), 900_000, 50_000, SpendingFocus.FIXED_COST);

        BudgetResponse response = budgetService.getBudget(userId, THIS_MONTH);

        assertThat(response.monthlyBudget()).isEqualTo(900_000);
        assertThat(response.savingsGoal()).isEqualTo(50_000);
        assertThat(response.spendingFocus()).isEqualTo("FIXED_COST");
    }

    @Test
    @DisplayName("복사는 1회만 — 두 번 조회해도 이번 달 행은 하나다")
    void copyIsIdempotent() {
        givenBudget(THIS_MONTH.minusMonths(1), 1_200_000, 200_000, SpendingFocus.DELIVERY);

        budgetService.getBudget(userId, THIS_MONTH);
        budgetService.getBudget(userId, THIS_MONTH);

        assertThat(budgetRepository.findAll())
                .extracting(Budget::getYearMonth)
                .containsExactlyInAnyOrder(THIS_MONTH.minusMonths(1).toString(), THIS_MONTH.toString());
    }

    @Test
    @DisplayName("복사된 행은 독립적이다 — 이번 달을 수정해도 원본 달은 그대로다")
    void copiedRowIsIndependent() {
        YearMonth lastMonth = THIS_MONTH.minusMonths(1);
        givenBudget(lastMonth, 1_200_000, 200_000, SpendingFocus.DELIVERY);
        budgetService.getBudget(userId, THIS_MONTH);

        givenBudget(THIS_MONTH, 700_000, 0, null);

        assertThat(budgetService.getBudget(userId, lastMonth).monthlyBudget()).isEqualTo(1_200_000);
        assertThat(budgetService.getBudget(userId, THIS_MONTH).monthlyBudget()).isEqualTo(700_000);
    }

    @Test
    @DisplayName("과거 월은 복사 대상이 아니다 — 더 앞선 달이 있어도 404")
    void pastMonthIsNotAutoCreated() {
        givenBudget(THIS_MONTH.minusMonths(3), 1_200_000, 200_000, SpendingFocus.DELIVERY);
        YearMonth lastMonth = THIS_MONTH.minusMonths(1);

        assertThatThrownBy(() -> budgetService.getBudget(userId, lastMonth))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        assertThat(budgetRepository.findByUserIdAndYearMonth(userId, lastMonth.toString())).isEmpty();
    }

    @Test
    @DisplayName("한 번도 예산이 없으면 복사 원본이 없어 404 (초기 설정 화면으로)")
    void noSourceMeansNotFound() {
        assertThatThrownBy(() -> budgetService.getBudget(userId, THIS_MONTH))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("findOrCopy: ANL-01 용 — 없음을 예외가 아니라 빈 Optional 로 준다 (remainingBudget null)")
    void findOrCopyReturnsEmptyInsteadOfThrowing() {
        assertThat(budgetService.findOrCopy(userId, THIS_MONTH)).isEmpty();
        assertThat(budgetService.findOrCopy(userId, THIS_MONTH.minusMonths(1))).isEmpty();
    }

    @Test
    @DisplayName("findOrCopy: 이번 달은 복사까지 해 주고, 과거 월은 행을 만들지 않는다")
    void findOrCopyAppliesTheSameCopyRule() {
        givenBudget(THIS_MONTH.minusMonths(1), 1_200_000, 200_000, SpendingFocus.DELIVERY);

        assertThat(budgetService.findOrCopy(userId, THIS_MONTH))
                .get()
                .extracting(Budget::getMonthlyBudget)
                .isEqualTo(1_200_000);
        assertThat(budgetService.findOrCopy(userId, THIS_MONTH.minusMonths(2))).isEmpty();
        assertThat(budgetRepository.findByUserIdAndYearMonth(userId, THIS_MONTH.minusMonths(2).toString()))
                .isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 예산은 복사 원본이 되지 않는다")
    void doesNotCopyAcrossUsers() {
        givenBudget(THIS_MONTH.minusMonths(1), 1_200_000, 200_000, SpendingFocus.DELIVERY);
        Long otherUserId = userRepository.save(
                User.signUp("이영희", "other-copy@email.com", "irrelevant-hash")).getId();

        assertThatThrownBy(() -> budgetService.getBudget(otherUserId, THIS_MONTH))
                .isInstanceOf(BusinessException.class);
    }
}
