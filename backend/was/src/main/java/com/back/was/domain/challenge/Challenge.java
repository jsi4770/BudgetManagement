package com.back.was.domain.challenge;

import com.back.was.domain.category.Category;
import com.back.was.domain.coaching.Coaching;
import com.back.was.domain.coaching.GoalType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD 9장 CHALLENGE — 확정된 절약 챌린지 한 건.
 *
 * <p><b>{@code USER_ID} 가 없다</b>(ERD 9장). 사용자는 {@code COACHING.USER_ID} 를 거쳐서
 * 안다 — 그래서 "이 사용자의 진행 중 챌린지"는 언제나 COACHING 을 조인하는 쿼리다
 * (서버_내부_처리 6장). 편의상 FK 를 하나 더 두고 싶어질 수 있는데, 그러면 코칭의 주인과
 * 챌린지의 주인이 어긋날 수 있는 상태가 생긴다.
 *
 * <p>{@code COACHING_ID} 는 <b>UNIQUE</b> 다 — 그게 COACHING 1 : 0..1 CHALLENGE 를
 * 보장하는 장치고(ERD 9장), 한 대화에서 챌린지를 두 번 확정할 수 없다는 뜻이다.
 *
 * <p>{@code CATEGORY_ID} 와 {@code GOAL_*} 는 COACHING 의 제안 값과 겹쳐 보이지만
 * <b>확정 시점의 스냅샷</b>이다(ERD 9장: 주문서가 주문 당시의 상품명과 가격을 따로 적는
 * 것과 같다). 사용자가 ± 로 조정한 값이 여기 들어오므로 제안 값과 다를 수 있고, 확정
 * 이후의 실적·판정 기준은 <b>이쪽</b>이다.
 *
 * <p>{@code CREATED_AT} 은 확정 시각이자 <b>코칭의 종료 시각</b>이다(ERD 7장 "시작·종료
 * 시각" 표) — 코칭에 종료 시각 컬럼이 없는 이유가 이것이다.
 *
 * <p><b>실적 세 컬럼({@code SPENDING_AMOUNT} · {@code SPENDING_COUNT} ·
 * {@code ACHIEVEMENT_RATE})을 갱신하는 코드는 아직 없다</b> — 거래 후처리 2번이고
 * (서버_내부_처리 1장) 그래서 지금은 기본값 0 에 머문다. 들어올 자리는
 * {@code TransactionService} 의 {@code nudgeJudgement.judge(...)} 옆이다.
 */
@Entity
@Table(
        name = "challenges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_challenges_coaching",
                columnNames = "coaching_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Challenge {

    /** ERD 9장 {@code ACHIEVEMENT_RATE} 는 {@code DECIMAL(10,2)} — CLAUDE.md 의 비율 규칙과 같다. */
    private static final int RATE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 챌린지를 확정한 대화. 사용자를 알아내는 유일한 경로다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coaching_id", nullable = false)
    private Coaching coaching;

    /** 확정 시점의 대상 카테고리 (스냅샷). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** ERD 9장 CHECK ≥ {@code START_DATE}. <b>이 날까지 포함</b>한다. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false, length = 10)
    private GoalType goalType;

    @Column(name = "goal_amount")
    private Integer goalAmount;

    @Column(name = "goal_count")
    private Integer goalCount;

    /** 후처리 2번이 갱신한다. 그때까지 0 이다. */
    @Column(name = "spending_amount", nullable = false)
    private int spendingAmount;

    @Column(name = "spending_count", nullable = false)
    private int spendingCount;

    /** {@code SPENDING ÷ GOAL × 100} — 화면의 "소비 한도의 60% 사용" (ERD 9장). */
    @Column(name = "achievement_rate", nullable = false, precision = 10, scale = RATE_SCALE)
    private BigDecimal achievementRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeStatus status;

    /** 확정 시각 = 코칭 종료 시각 (ERD 7장·9장). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Challenge(Coaching coaching, Category category, String title, GoalType goalType,
                      Integer goalAmount, Integer goalCount, LocalDate startDate, LocalDate endDate) {
        this.coaching = coaching;
        this.category = category;
        this.title = title;
        this.goalType = goalType;
        this.goalAmount = goalType == GoalType.AMOUNT ? goalAmount : null;
        this.goalCount = goalType == GoalType.COUNT ? goalCount : null;
        this.startDate = startDate;
        this.endDate = endDate;
        this.spendingAmount = 0;
        this.spendingCount = 0;
        this.achievementRate = BigDecimal.ZERO.setScale(RATE_SCALE);
        this.status = ChallengeStatus.IN_PROGRESS;
    }

    /**
     * 확정 시점의 값을 그대로 복사한다 (COACH-04 / ERD 9장 스냅샷).
     *
     * <p>제안({@code Coaching} 의 {@code PROPOSAL_} 컬럼)을 읽지 않고 <b>인자로 받는다</b> —
     * 사용자가 ± 로 조정한 최종 값이 들어와야 하고, 제안을 참조하면 "조정분이 무시되는"
     * 버그가 조용히 생긴다.
     *
     * <p>목표 유형과 맞지 않는 쪽 컬럼은 비운다 — {@code Coaching.propose} 와 같은 이유다.
     *
     * @throws IllegalArgumentException 유형에 맞는 목표 값이 없거나(ERD 9장 체크 조건)
     *                                  {@code endDate} 가 {@code startDate} 보다 앞일 때
     *                                  (ERD 9장 CHECK)
     */
    public static Challenge of(Coaching coaching, Category category, String title, GoalType goalType,
                               Integer goalAmount, Integer goalCount,
                               LocalDate startDate, LocalDate endDate) {
        goalType.requiredGoal(goalAmount, goalCount);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "종료일은 시작일 이후여야 해요 (ERD 9장 CHECK END_DATE >= START_DATE).");
        }
        return new Challenge(coaching, category, title, goalType, goalAmount, goalCount,
                startDate, endDate);
    }

    /**
     * 거래 후처리 <b>2번</b>: 기간 안의 실적을 다시 써 넣는다 (서버_내부_처리 2장).
     *
     * <p><b>누적이 아니라 덮어쓰기</b>다. 호출자가 챌린지 기간 전체를 다시 집계해서
     * 넘기므로(거래 수정·삭제도 같은 경로를 탄다) 더하는 방식이면 지운 거래가 영원히
     * 남는다.
     *
     * <p>{@code STATUS} 는 건드리지 않는다. <b>목표를 초과해도 자동으로 실패·취소되지
     * 않는다</b> — 판정은 기간이 끝난 다음 날 00:05 에 스케줄러가 한 번만 하고
     * ({@link #finish()}), 챌린지 상태에 {@code CANCELED} 라는 값 자체가 없다(ERD 9장).
     * 초과는 {@code ACHIEVEMENT_RATE} 가 100 을 넘은 {@code IN_PROGRESS} 로 표현된다.
     *
     * <p>그래서 비율에 <b>상한을 두지 않는다</b>. 100 에서 잘라 버리면 "얼마나 넘겼는지"가
     * 응답에서 사라지고, 화면의 "소비 한도의 N% 사용"이 초과 상황을 설명할 수 없게 된다.
     *
     * @param spendingAmount 기간 안의 금액 합계
     * @param spendingCount  기간 안의 거래 건수
     * @throws IllegalStateException 이미 종료된 챌린지일 때 — 종료된 챌린지는 거래가
     *                               바뀌어도 재계산하지 않는다(서버_내부_처리 1장 ·
     *                               ERD 9장 "늦은 기록은 반영하지 않아요"). 호출자가
     *                               {@code IN_PROGRESS} 만 조회하므로 여기 예외는 마지막
     *                               방어선이다
     */
    public void updatePerformance(int spendingAmount, int spendingCount) {
        if (isFinished()) {
            throw new IllegalStateException(
                    "종료된 챌린지의 실적은 다시 계산하지 않아요 (status=%s).".formatted(status));
        }
        this.spendingAmount = spendingAmount;
        this.spendingCount = spendingCount;
        this.achievementRate = rateOf(spendingAmount, spendingCount);
    }

    /**
     * ERD 9장: {@code ACHIEVEMENT_RATE} = {@code SPENDING ÷ GOAL × 100}.
     *
     * <p>어느 쪽 실적과 어느 쪽 목표를 쓰는지는 {@code GOAL_TYPE} 이 정한다 —
     * {@link #finish()} 가 판정에 쓰는 짝과 같아야 하므로 둘 다 {@code goalType} 에
     * 물어본다.
     *
     * <p>{@code HALF_UP} scale 2 는 이 프로젝트의 비율 관례다(CLAUDE.md ·
     * {@code SpendingComparison} 의 증가율과 같은 방식). 목표는 1 이상이 보장되므로
     * ({@link GoalType#requiredGoal}) 0 으로 나눌 일은 없다.
     */
    private BigDecimal rateOf(int spendingAmount, int spendingCount) {
        int goal = goalType.requiredGoal(goalAmount, goalCount);
        int spending = goalType == GoalType.AMOUNT ? spendingAmount : spendingCount;

        return BigDecimal.valueOf(spending)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(goal), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 종료 판정 — <b>일일 스케줄러의 자리</b>다 (ERD 9장 / SRS 7.4-4): 실적이 목표 이하면
     * {@code SUCCESS}, 아니면 {@code FAILED}. 어느 실적을 보는지는 목표 유형이 정한다.
     *
     * <p>스케줄러 자체는 아직 없다(남은_작업_계획 2-4). 판정 규칙만 엔티티에 두는 이유는
     * 규칙이 ERD 에 있고 상태 전이를 엔티티 밖에서 하면 되돌릴 수 없는 전이를 아무나 할 수
     * 있게 되기 때문이다 — 한 번 판정된 챌린지는 얼어붙어야 한다.
     *
     * @throws IllegalStateException 이미 종료된 챌린지일 때 (늦은 기록으로 결과를 다시
     *                               쓰는 일을 막는다 — ERD 9장)
     */
    public void finish() {
        if (isFinished()) {
            throw new IllegalStateException(
                    "이미 종료된 챌린지는 다시 판정하지 않아요 (status=%s).".formatted(status));
        }
        int goal = goalType.requiredGoal(goalAmount, goalCount);
        int spending = goalType == GoalType.AMOUNT ? spendingAmount : spendingCount;
        this.status = spending <= goal ? ChallengeStatus.SUCCESS : ChallengeStatus.FAILED;
    }

    /** 판정이 끝나 얼어붙은 상태인지. 후처리 2번이 건너뛰는 조건이다(서버_내부_처리 1장). */
    public boolean isFinished() {
        return status != ChallengeStatus.IN_PROGRESS;
    }
}
