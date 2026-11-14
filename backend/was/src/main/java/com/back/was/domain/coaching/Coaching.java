package com.back.was.domain.coaching;

import com.back.was.domain.category.Category;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ERD 7장 COACHING — AI 코치와의 대화 세션 한 건.
 *
 * <p><b>넛지와의 FK 가 없다</b>(ERD 7장). 넛지에서 시작했더라도 넛지 내용은 첫 AI 메시지의
 * 맥락으로만 쓰이고 저장되지 않는다 — 그래서 COACH-01 의 {@code nudgeId} 는 요청에만
 * 있고 이 엔티티에는 흔적이 남지 않는다. 남는 흔적은 그 넛지의 상태가
 * {@code COACHING_STARTED} 로 바뀐 것뿐이다.
 *
 * <p><b>{@code PROPOSAL_} 컬럼 6개는 최신 제안 하나만 보관한다</b>(ERD 7장). AI 가 목표를
 * 다시 제안하면 덮어쓰므로 제안 이력은 남지 않는다 — 대화 이력은
 * {@link CoachingMessage} 가 가지고 있고, 화면이 복원해야 하는 제안 카드는 언제나 마지막
 * 하나다(명세 COACH-02).
 *
 * <p>확정(COACH-04)하면 사용자가 보낸 <b>최종</b> 값이 CHALLENGE 로 복사된다. 그래서 이
 * 제안 값과 챌린지 값은 다를 수 있고(사용자가 ± 로 조정), 그 뒤의 실적·판정 기준은
 * <b>CHALLENGE 값</b>이다(ERD 7장). 여기 남는 값은 "AI 제안을 그대로 확정한 비율" 같은
 * 기록용이다.
 *
 * <p>시작 시각은 {@code CREATED_AT} 이다(명세 COACH-01 {@code createdAt}). 종료 시각 컬럼은
 * 없다 — {@code COMPLETED} 는 {@code CHALLENGE.CREATED_AT} 으로 알 수 있고
 * {@code CANCELED} 는 쓰는 곳이 없다(ERD 7장).
 */
@Entity
@Table(name = "coachings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coaching {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * AI 가 제안한 대상 카테고리. 제안 전에는 {@code null} 이다 — ERD 7장이 FK 를
     * NULL 허용으로 둔 이유이고, 그래서 {@code optional = true} 다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_category_id")
    private Category proposalCategory;

    @Column(name = "proposal_title", length = 100)
    private String proposalTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_goal_type", length = 10)
    private GoalType proposalGoalType;

    @Column(name = "proposal_goal_amount")
    private Integer proposalGoalAmount;

    @Column(name = "proposal_goal_count")
    private Integer proposalGoalCount;

    /** ERD 7장 CHECK &gt; 0. 명세 COACH-04 가 쓰는 값은 7·14·30 이지만 제안은 일수만 담는다. */
    @Column(name = "proposal_period_days")
    private Integer proposalPeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoachingStatus status;

    /** <b>코칭 시작 시각</b>이다(ERD 7장) — 명세 COACH-01·02 의 {@code createdAt}. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Coaching(User user) {
        this.user = user;
        this.status = CoachingStatus.IN_PROGRESS;
    }

    /**
     * COACH-01. 제안 컬럼 6개는 모두 {@code null} 로 시작한다(ERD 7장 "제안 전에는 모두
     * NULL") — 명세 COACH-01 응답의 {@code proposal: null} 이 그것이다.
     *
     * <p>넛지를 받지 않는다. 넛지는 첫 메시지를 만드는 데만 쓰이고 세션에 저장되지 않으므로
     * (ERD 7장) 생성자가 알아야 할 이유가 없다.
     */
    public static Coaching start(User user) {
        return new Coaching(user);
    }

    /**
     * 확정 없이 새 코칭이 시작돼 밀려났다 (ERD 7장 "상태 변경 규칙").
     *
     * <p>취소 시각을 남기지 않는 건 ERD 의 결정이다 — 읽는 화면이 없다. 이미 종료된 코칭을
     * 다시 취소하는 일은 없어야 하므로({@code COMPLETED} 를 덮으면 확정한 챌린지의 주인이
     * 취소된 세션에 걸린다) 상태를 확인하고 막는다.
     */
    public void cancel() {
        if (isEnded()) {
            throw new IllegalStateException("이미 종료된 코칭은 취소할 수 없어요 (status=%s).".formatted(status));
        }
        this.status = CoachingStatus.CANCELED;
    }

    /**
     * 챌린지 확정으로 종료됐다 (COACH-04).
     *
     * <p>종료 시각을 저장하지 않는다 — {@code CHALLENGE.CREATED_AT} 이 그 값이다(ERD 7장
     * "시작·종료 시각" 표). 그래서 이 메서드를 부르는 쪽은 같은 트랜잭션에서 챌린지를
     * <b>반드시</b> 만들어야 한다. 챌린지 없는 {@code COMPLETED} 코칭은 종료 시각을 잃은
     * 행이고, CHALLENGE 의 {@code COACHING_ID} UNIQUE 가 그 반대(한 코칭에 챌린지 둘)를
     * 막아 준다.
     *
     * <p>{@link #cancel()} 과 같은 가드를 둔다 — 이미 종료된 세션을 다시 확정하면 챌린지가
     * 두 개 생길 수 있고, 그건 "사용자당 진행 중 챌린지 1개"(ERD 9장)를 깬다. 호출자는
     * 그 전에 {@link #isEnded()} 로 409 {@code COACHING_ALREADY_ENDED} 를 내보내므로 여기
     * 예외는 마지막 방어선이다.
     */
    public void complete() {
        if (isEnded()) {
            throw new IllegalStateException("이미 종료된 코칭은 확정할 수 없어요 (status=%s).".formatted(status));
        }
        this.status = CoachingStatus.COMPLETED;
    }

    /**
     * 종료된 세션인지 — {@code COMPLETED} 와 {@code CANCELED} 를 묶는다.
     *
     * <p>COACH-03·04 가 409 {@code COACHING_ALREADY_ENDED} 를 낼 조건이다
     * (서버_내부_처리 6장 "종료된 코칭에 요청"). 두 상태를 각자 비교하면 나중에 상태가
     * 하나 늘었을 때 한쪽만 고치게 된다.
     */
    public boolean isEnded() {
        return status != CoachingStatus.IN_PROGRESS;
    }

    /**
     * AI 목표 제안을 <b>덮어쓴다</b> — ERD 7장 "최신 제안 1개만 보관". COACH-03 에서 AI 가
     * 목표를 제안할 때마다 호출되는 자리다.
     *
     * <p>비우는 경로는 없다. 제안이 한 번 생기면 사용자가 확정하거나 세션이 끝날 때까지
     * 카드가 남아야 하고(명세 COACH-02 "저장된 최신 {@code proposal} 포함 → 제안 카드
     * 복원"), AI 가 다음 턴에 제안을 하지 않는 것은 "제안 취소"가 아니다.
     *
     * @throws IllegalArgumentException 유형에 맞는 목표 값이 비었거나(ERD 7장 체크 조건)
     *                                  기간이 1일 미만일 때(ERD 7장 CHECK &gt; 0)
     */
    public void propose(Category category, String title, GoalType goalType,
                        Integer goalAmount, Integer goalCount, int periodDays) {
        goalType.requiredGoal(goalAmount, goalCount);
        if (periodDays < 1) {
            throw new IllegalArgumentException("제안 기간은 1일 이상이어야 해요 (ERD 7장 CHECK > 0).");
        }

        this.proposalCategory = category;
        this.proposalTitle = title;
        this.proposalGoalType = goalType;
        // 유형과 맞지 않는 쪽은 비운다 — 이전 제안이 AMOUNT, 새 제안이 COUNT 일 때
        // 지난 금액이 남아 있으면 체크 조건은 통과하지만 화면에 두 값이 다 뜬다.
        this.proposalGoalAmount = goalType == GoalType.AMOUNT ? goalAmount : null;
        this.proposalGoalCount = goalType == GoalType.COUNT ? goalCount : null;
        this.proposalPeriodDays = periodDays;
    }

    /** 제안이 한 번이라도 있었는지. 응답의 {@code proposal} 키가 {@code null} 인지를 정한다. */
    public boolean hasProposal() {
        return proposalGoalType != null;
    }
}
