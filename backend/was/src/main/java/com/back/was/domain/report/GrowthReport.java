package com.back.was.domain.report;

import com.back.was.domain.challenge.Challenge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ERD 10장 GROWTH_REPORT — 종료된 챌린지 한 건의 성장 리포트.
 *
 * <p><b>챌린지가 끝날 때 한 번만 만들어진다</b>(서버_내부_처리 4장). {@code CHALLENGE_ID} 가
 * UNIQUE 라 한 챌린지에 리포트는 하나이고, 진행 중인 챌린지에는 아예 없다 — 그게 CHL-03 이
 * 진행 중 챌린지에 404 를 주는 이유다(명세 CHL-03 Errors).
 *
 * <p><b>{@code BEFORE_} 두 값이 이 테이블의 존재 이유다.</b> 나머지 숫자는 전부 CHALLENGE 에
 * 있지만 "챌린지 시작 직전 같은 길이 기간의 지출"은 종료 시점에 계산해 <b>박아 두어야</b>
 * 한다 — 나중에 다시 집계하면 그 사이에 추가·수정된 과거 거래 때문에 값이 바뀌고, 이미
 * 사용자에게 보여 준 "챌린지 전 107,000원"이 조용히 달라진다.
 *
 * <p>{@code FEEDBACK} 은 Gemini 가 쓰고 실패하면 {@code docs/AI_대체_문구.md} 7장 템플릿이
 * 들어온다(ERD 10장). 넛지 문구와 달리 <b>비동기가 아니다</b> — 생성 주체가 일일 스케줄러라
 * 응답 지연이 없고, 포트가 실패하지 않으므로 리포트가 문구 없이 저장될 일도 없다.
 *
 * <p>{@code CREATED_AT} 은 리포트 생성 시각 = 판정 시각이다(ERD 10장 예시
 * {@code 2026-11-11 00:05:00} — 종료일 다음 날 00:05).
 */
@Entity
@Table(
        name = "growth_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_growth_reports_challenge",
                columnNames = "challenge_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrowthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 1 : 0..1 이라 {@code @OneToOne} 이다. 주인은 리포트 쪽이고(FK 를 들고 있다) 챌린지에는
     * 역방향 필드를 두지 않는다 — CHL-01·02 가 리포트를 쓰지 않으므로 거기서 지연 로딩이
     * 생길 이유가 없다.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    /** 챌린지 시작일 직전, <b>같은 길이 기간</b>의 같은 카테고리 지출 금액 (ERD 10장). */
    @Column(name = "before_amount", nullable = false)
    private int beforeAmount;

    /** 위 기간의 지출 횟수. */
    @Column(name = "before_count", nullable = false)
    private int beforeCount;

    /** AI 피드백. 생성 실패 시 {@code docs/AI_대체_문구.md} 7장 템플릿 (ERD 10장). */
    @Column(columnDefinition = "text")
    private String feedback;

    /** 리포트 생성일 = 판정 시각 (ERD 10장). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private GrowthReport(Challenge challenge, int beforeAmount, int beforeCount, String feedback) {
        this.challenge = challenge;
        this.beforeAmount = beforeAmount;
        this.beforeCount = beforeCount;
        this.feedback = feedback;
    }

    /**
     * 종료 판정이 리포트를 만든다 (서버_내부_처리 4장).
     *
     * <p><b>종료된 챌린지만</b> 받는다. 진행 중인 챌린지로 리포트를 만들면 CHL-03 이
     * "진행 중이면 없음"(명세 CHL-03)을 어기고, {@code BEFORE_} 값도 아직 확정될 수 없는
     * 시점의 숫자가 된다.
     *
     * @throws IllegalArgumentException 챌린지가 아직 {@code IN_PROGRESS} 일 때
     */
    public static GrowthReport of(Challenge challenge, int beforeAmount, int beforeCount,
                                  String feedback) {
        if (!challenge.isFinished()) {
            throw new IllegalArgumentException(
                    "진행 중인 챌린지로는 성장 리포트를 만들지 않아요 (status=%s)."
                            .formatted(challenge.getStatus()));
        }
        return new GrowthReport(challenge, beforeAmount, beforeCount, feedback);
    }
}
