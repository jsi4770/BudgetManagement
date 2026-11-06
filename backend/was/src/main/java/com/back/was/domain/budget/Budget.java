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

/**
 * ERD 3장 BUDGET.
 *
 * <p><b>현재는 AUTH-02 의 {@code hasBudget} 판정에만 쓰인다.</b> BUDGET-01·02
 * 엔드포인트와 ERD 3장의 "조회 시 최근 월 예산을 복사해 이번 달 행 생성" 규칙은
 * 아직 구현되지 않았다. 컬럼은 ERD 를 그대로 따랐으므로 그 기능이 올라올 때
 * 테이블을 바꿀 필요는 없다.
 *
 * <p>{@code YEAR_MONTH} 는 일부 DB에서 예약어이자 Java 의 {@code java.time.YearMonth}
 * 와 이름이 겹친다. 컬럼명을 명시하고 타입은 ERD 대로 {@code CHAR(7)} 문자열로 둔다.
 */
@Entity
@Table(
        name = "budget",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budget_user_year_month",
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
}
