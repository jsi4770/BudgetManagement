package com.back.was.domain.transaction;

import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.Months;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분석 API(ANL-01/02)가 쓰는 집계 쿼리.
 *
 * <p>ANL-03 과 넛지 판정이 쓰던 {@code sumExpensesByCategory}(카테고리별 지출) 의 테스트도
 * 여기 있었다 — 쿼리와 함께 빠졌다. 되살릴 때의 규칙("거래가 없는 카테고리는 줄이 아예 없다
 * → 0원 채우기는 호출자 몫")은 {@code docs/AI_코칭_소비분석_인수인계.md} 1장 ① 에 있다.
 *
 * <p>고정 데이터는 docs/시연_데이터.md 6장의 11월 1~4일이다 — 총지출 666,150원(월세 포함),
 * 수입 2,400,000원, 월세 제외 116,150원. 명세 ANL-01 응답 예시의 숫자와 같아서, 이 쿼리가
 * 맞으면 ANL-01 의 {@code totalIncome}·{@code totalExpense} 도 맞는다.
 *
 * <p>날짜를 {@code KstTime.today()} 가 아니라 과거 고정 날짜로 쓰는 이유: 쿼리는 "오늘"을
 * 모르고 받은 구간만 집계하므로, 실행 날짜에 따라 결과가 흔들리면 안 된다.
 */
@SpringBootTest
@Transactional
class TransactionAnalysisQueryTest {

    /** 시연일 D (수요일). */
    private static final LocalDate D = LocalDate.of(2026, 11, 4);

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;
    private static final long SALARY = 7L;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private User user;
    private User other;

    @BeforeEach
    void givenDemoTransactions() {
        user = userRepository.save(User.signUp("홍길동", "anl@email.com", "irrelevant-hash"));
        other = userRepository.save(User.signUp("이영희", "anl-other@email.com", "irrelevant-hash"));

        // 시연 6-3 의 11월 1~4일
        given(user, RentCategory.ID, 550_000, LocalDate.of(2026, 11, 1));
        given(user, SALARY, 2_400_000, LocalDate.of(2026, 11, 1));
        given(user, DELIVERY, 24_000, LocalDate.of(2026, 11, 2));
        given(user, TRANSPORT, 1_650, LocalDate.of(2026, 11, 2));
        given(user, DELIVERY, 32_000, LocalDate.of(2026, 11, 3));
        given(user, FOOD, 9_000, LocalDate.of(2026, 11, 3));
        given(user, CAFE, 4_500, LocalDate.of(2026, 11, 3));
        given(user, DELIVERY, 45_000, D);

        // 범위 밖(지난 달) · 다른 사용자 — 어떤 집계에도 섞이면 안 된다
        given(user, DELIVERY, 77_000, LocalDate.of(2026, 10, 31));
        given(other, DELIVERY, 99_000, LocalDate.of(2026, 11, 3));
        given(other, SALARY, 99_000, LocalDate.of(2026, 11, 3));
    }

    private void given(User owner, long categoryId, int amount, LocalDate date) {
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        transactionRepository.save(Transaction.of(owner, category, amount, date, null));
    }

    private AmountSummary sumAmounts(User owner, DateRange range) {
        return transactionRepository.sumAmounts(owner.getId(), range.start(), range.end(),
                CategoryType.INCOME, CategoryType.EXPENSE);
    }

    private List<DailyAmount> sumDailyExpenses(DateRange range, Long excludedCategoryId) {
        return transactionRepository.sumDailyExpenses(user.getId(), range.start(), range.end(),
                CategoryType.EXPENSE, excludedCategoryId);
    }

    // --- ANL-01 월 합계 -----------------------------------------------------

    @Test
    @DisplayName("명세 ANL-01 예시: 11월 수입 2,400,000 · 지출 666,150(월세 포함) · 잔액 1,733,850")
    void monthlyTotalsMatchTheSpecExample() {
        AmountSummary summary = sumAmounts(user, Months.monthOf(YearMonth.of(2026, 11)));

        assertThat(summary).isEqualTo(new AmountSummary(2_400_000, 666_150));
        assertThat(summary.balance()).isEqualTo(1_733_850);
    }

    @Test
    @DisplayName("범위 밖 거래와 다른 사용자 거래는 섞이지 않는다")
    void scopedByUserAndRange() {
        AmountSummary lastMonth = sumAmounts(user, Months.monthOf(YearMonth.of(2026, 10)));
        assertThat(lastMonth).isEqualTo(new AmountSummary(0, 77_000));

        AmountSummary othersMonth = sumAmounts(other, Months.monthOf(YearMonth.of(2026, 11)));
        assertThat(othersMonth).isEqualTo(new AmountSummary(99_000, 99_000));
    }

    @Test
    @DisplayName("거래가 한 건도 없는 달은 null 이 아니라 0원이다 (ANL-01 에 빈 달 404 는 없다)")
    void emptyMonthIsZeroNotNull() {
        AmountSummary summary = sumAmounts(user, Months.monthOf(YearMonth.of(2026, 1)));

        assertThat(summary).isEqualTo(new AmountSummary(0, 0));
    }

    // --- ANL-02 일별 지출 ---------------------------------------------------

    @Test
    @DisplayName("시연 6-2: 이번 주(11-02 ~ 11-04) 월세 제외 지출은 116,150 — 하루 합산, 거래 있는 날만")
    void dailyExpensesExcludeRent() {
        // D 가 수요일이므로 그 주의 월요일 ~ D = 11-02 ~ 11-04
        List<DailyAmount> daily = sumDailyExpenses(
                new DateRange(LocalDate.of(2026, 11, 2), D), RentCategory.ID);

        assertThat(daily).containsExactly(
                new DailyAmount(LocalDate.of(2026, 11, 2), 25_650),
                new DailyAmount(LocalDate.of(2026, 11, 3), 45_500),
                new DailyAmount(LocalDate.of(2026, 11, 4), 45_000));
        assertThat(daily.stream().mapToLong(DailyAmount::amount).sum()).isEqualTo(116_150);
    }

    @Test
    @DisplayName("제외 카테고리를 주지 않으면 월세도 더한다 — 11-01 은 월세만 있는 날이다")
    void nullExclusionKeepsEverything() {
        List<DailyAmount> daily = sumDailyExpenses(
                new DateRange(LocalDate.of(2026, 11, 1), D), null);

        assertThat(daily).first().isEqualTo(new DailyAmount(LocalDate.of(2026, 11, 1), 550_000));
        assertThat(daily).extracting(DailyAmount::date)
                .doesNotContain(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("수입은 지출 집계에 들어가지 않는다 — 급여가 있는 11-01 도 월세만 잡힌다")
    void incomeIsNeverCounted() {
        List<DailyAmount> daily = sumDailyExpenses(
                new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)), null);

        assertThat(daily).containsExactly(new DailyAmount(LocalDate.of(2026, 11, 1), 550_000));
    }

    @Test
    @DisplayName("to 이후 날짜는 더하지 않는다 — ANL-02 가 '이번 주 합계 = 월요일 ~ 오늘'을 범위로 표현하는 근거")
    void stopsAtTheEndOfRange() {
        List<DailyAmount> daily = sumDailyExpenses(
                new DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)), RentCategory.ID);

        assertThat(daily).extracting(DailyAmount::date).containsExactly(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));
        assertThat(daily.stream().mapToLong(DailyAmount::amount).sum()).isEqualTo(71_150);
    }

    @Test
    @DisplayName("거래가 없는 주는 빈 목록이다 — 0원 채우기는 호출자 몫 (ERD 6장)")
    void emptyWeekReturnsNoRows() {
        // 4주 전 같은 요일까지 = 10-05 ~ 10-07 (시드 데이터가 없는 구간)
        assertThat(sumDailyExpenses(
                new DateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)),
                RentCategory.ID)).isEmpty();
    }
}
