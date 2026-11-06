package com.back.was.domain.budget;

import com.back.was.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * ERD 3장 BUDGET.
 *
 * <p>{@code YEAR_MONTH} 는 일부 DB에서 예약어이자 Java 의 {@code java.time.YearMonth}
 * 와 이름이 겹친다. 컬럼명을 명시하고 저장 타입은 ERD 대로 {@code CHAR(7)} 문자열로 둔다.
 * 문자열이어도 {@code YYYY-MM} 은 사전순 = 시간순이라 "가장 최근 월" 조회
 * ({@link BudgetRepository#findTopByUserIdAndYearMonthLessThanOrderByYearMonthDesc})가
 * 그대로 성립한다.
 */
@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budgets_user_year_month",
                columnNames = {"user_id", "year_month"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** {@code YYYY-MM} (명세 0장 2) 연월 형식). */
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "monthly_budget", nullable = false)
    private int monthlyBudget;

    @Column(name = "savings_goal", nullable = false)
    private int savingsGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "spending_focus", length = 20)
    private SpendingFocus spendingFocus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Budget(User user, YearMonth yearMonth, int monthlyBudget, int savingsGoal, SpendingFocus spendingFocus) {
        this.user = user;
        this.yearMonth = yearMonth.toString();
        this.monthlyBudget = monthlyBudget;
        this.savingsGoal = savingsGoal;
        this.spendingFocus = spendingFocus;
    }

    /** BUDGET-02 저장(신규). */
    public static Budget of(User user, YearMonth yearMonth, int monthlyBudget, int savingsGoal,
                            SpendingFocus spendingFocus) {
        return new Budget(user, yearMonth, monthlyBudget, savingsGoal, spendingFocus);
    }

    /**
     * ERD 3장 "이번 달 행이 없으면 가장 최근 월 예산을 복사" — 금액·목표만 옮기고
     * 연월은 새 달로, {@code CREATED_AT}/{@code UPDATED_AT} 은 새로 찍힌다.
     */
    public Budget copyTo(YearMonth target) {
        return new Budget(user, target, monthlyBudget, savingsGoal, spendingFocus);
    }

    /**
     * BUDGET-02 저장(덮어쓰기). PUT 이라 보낸 값으로 전체를 바꾼다 — {@code spendingFocus}
     * 를 보내지 않으면 "유지"가 아니라 {@code null} 로 지워지는 게 PUT 의 의미다.
     */
    public void overwrite(int monthlyBudget, int savingsGoal, SpendingFocus spendingFocus) {
        this.monthlyBudget = monthlyBudget;
        this.savingsGoal = savingsGoal;
        this.spendingFocus = spendingFocus;
    }
}
