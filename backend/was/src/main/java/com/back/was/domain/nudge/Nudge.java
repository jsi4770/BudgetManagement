package com.back.was.domain.nudge;

import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.category.Category;
import com.back.was.domain.user.User;
import com.back.was.global.support.DateRange;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD 6장 NUDGE — "평소보다 많이 썼다"고 판정된 카드 한 장.
 *
 * <p>복합 UNIQUE {@code (USER_ID, CATEGORY_ID, ANALYSIS_START_DATE)} 로 <b>한 사용자의 한
 * 카테고리에 한 주당 한 장</b>이다. {@code ANALYSIS_END_DATE} 는 판정일마다 바뀌므로 키에
 * 들어가지 않는다 — 그래서 같은 주에 거래가 더 쌓이면 새 행이 아니라 이 행이 갱신된다.
 *
 * <p><b>카드 제목은 저장하지 않는다</b>(ERD 6장). 카테고리명으로 템플릿을 조합하는 건
 * 응답 쪽({@code NudgeResponse})의 일이다. 저장하면 카테고리명이 바뀌었을 때 과거 카드의
 * 제목만 옛 이름으로 남는다.
 *
 * <p>{@code AI_ANALYSIS}·{@code AI_RECOMMENDATION} 은 커밋 후 비동기로 채워지므로
 * <b>생성 직후에는 {@code null}</b> 이다(ERD 6장 · 서버_내부_처리 8장). NDG-02 가 그
 * 상태를 "AI가 분석 중이에요"로 표현한다.
 */
@Entity
@Table(
        name = "nudges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_nudges_user_category_analysis_start",
                columnNames = {"user_id", "category_id", "analysis_start_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 이상 소비로 판정된 카테고리. 월세(ID 5)와 수입 카테고리는 판정 대상이 아니다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** 분석 기간 시작 = 그 주 월요일. 복합 UNIQUE 의 일부라 "한 주당 한 장"의 기준이 된다. */
    @Column(name = "analysis_start_date", nullable = false)
    private LocalDate analysisStartDate;

    /** 분석 기간 종료 = 마지막 판정일. 같은 주 안에서 계속 갱신된다. */
    @Column(name = "analysis_end_date", nullable = false)
    private LocalDate analysisEndDate;

    @Column(name = "spending_amount", nullable = false)
    private int spendingAmount;

    /** 직전 4주 같은 기간 평균 — 화면 문구는 "평소 이맘때 평균"이다(명세 NDG-01). */
    @Column(name = "avg_amount", nullable = false)
    private int avgAmount;

    /** 판정 점수. 화면에는 나가지 않는다(명세 NDG-01 "Z-score는 내부 판정용으로 미포함"). */
    @Column(name = "z_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal zScore;

    @Column(name = "growth_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal growthRate;

    @Column(name = "ai_analysis", columnDefinition = "text")
    private String aiAnalysis;

    @Column(name = "ai_recommendation", columnDefinition = "text")
    private String aiRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NudgeStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** NDG-01 의 "여러 개면 최신 1개" 기준이다(ERD 6장 "홈 노출 순서 기준"). */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Nudge(User user, Category category, DateRange analysisPeriod,
                  SpendingComparison comparison) {
        this.user = user;
        this.category = category;
        this.analysisStartDate = analysisPeriod.start();
        this.analysisEndDate = analysisPeriod.end();
        this.spendingAmount = comparison.amount();
        this.avgAmount = comparison.average();
        this.zScore = comparison.zScore();
        this.growthRate = comparison.growthRate();
        this.status = NudgeStatus.UNREAD;
    }

    /**
     * 판정 결과로 카드를 만든다.
     *
     * <p>네 숫자({@code spendingAmount} · {@code avgAmount} · {@code zScore} ·
     * {@code growthRate})를 따로 받지 않고 {@link SpendingComparison} 하나로 받는 건
     * <b>서로 어긋날 수 없게</b> 하기 위함이다 — 명세는 이 값들이 ANL-03 화면의 숫자와
     * 같아야 한다고 요구한다(서버_내부_처리 3장 "같은 계산 함수").
     *
     * <p>{@code zScore} 가 {@code null}(= 표본의 표준편차 0)이면 ERD 6장의 "판정 생략 ②"
     * 상황이라 애초에 카드를 만들 일이 없다. 컬럼이 NOT NULL 이기도 해서 여기서 막는다.
     * 표준편차가 0보다 크면 평균도 0보다 크므로 {@code growthRate} 는 함께 채워진다.
     *
     * @param analysisPeriod 그 주 월요일 ~ 판정일
     * @throws IllegalArgumentException {@code comparison.zScore()} 가 {@code null} 일 때
     */
    public static Nudge of(User user, Category category, DateRange analysisPeriod,
                           SpendingComparison comparison) {
        if (comparison.zScore() == null) {
            throw new IllegalArgumentException(
                    "표준편차가 0이면 넛지를 만들지 않아요 (ERD 6장 판정 생략).");
        }
        return new Nudge(user, category, analysisPeriod, comparison);
    }

    /**
     * 사용자 반응 기록 (NDG-03). 판정이 다시 돌아도 이 값은 유지된다 — 되돌리는 경로는
     * 없으므로 닫은 카드가 다시 뜨지 않는다(ERD 6장).
     */
    public void changeStatus(NudgeStatus status) {
        this.status = status;
    }
}
