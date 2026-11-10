package com.back.was.domain.coaching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * ERD 8장 COACHING_MESSAGE — 대화 한 줄.
 *
 * <p><b>수정되지 않는다.</b> 만드는 메서드만 있고 내용을 바꾸는 메서드가 없다 — ERD 8장이
 * "메시지는 수정하지 않아서 {@code UPDATED_AT} 이 보통 {@code CREATED_AT} 과 같다"고 적어
 * 둔 것을 코드로 막은 것이다. 대화는 AI 에게 넘길 맥락이자 재진입 복원용 기록이므로
 * (SFR-023·025) 과거 발화가 바뀌면 둘 다 거짓이 된다.
 *
 * <p>정렬 기준은 {@code CREATED_AT} 이고(ERD 8장) 인덱스도 {@code (COACHING_ID,
 * CREATED_AT)} 이다. 같은 요청에서 연달아 저장된 두 메시지는 초 단위가 같을 수 있어서,
 * 조회할 때 ID 를 2차 기준으로 쓴다
 * ({@link CoachingMessageRepository#findByCoachingIdOrderByCreatedAtAscIdAsc}).
 *
 * <p>AI 메시지가 대체 문구였는지({@code isFallback})는 저장하지 않는다 — ERD 8장의 결정이고
 * 명세 COACH-03 에서도 응답 전용이다. 다시 열었을 때 "그때 AI 가 쉬고 있었다"는 사실을
 * 보여 줄 화면이 없다.
 */
@Entity
@Table(
        name = "coaching_messages",
        indexes = @Index(
                name = "idx_coaching_messages_coaching_created_at",
                columnList = "coaching_id, created_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoachingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coaching_id", nullable = false)
    private Coaching coaching;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageRole role;

    /** ERD 8장은 TEXT 다 — 사용자 입력은 500자지만(명세 COACH-03) AI 응답은 상한이 없다. */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 대화 순서의 기준이다 (ERD 8장). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CoachingMessage(Coaching coaching, MessageRole role, String content) {
        this.coaching = coaching;
        this.role = role;
        this.content = content;
    }

    /** AI 코치의 발화. COACH-01 의 첫 메시지와 COACH-03 의 응답이 모두 이것이다. */
    public static CoachingMessage ai(Coaching coaching, String content) {
        return new CoachingMessage(coaching, MessageRole.AI, content);
    }

    /** 사용자의 발화 (COACH-03). */
    public static CoachingMessage user(Coaching coaching, String content) {
        return new CoachingMessage(coaching, MessageRole.USER, content);
    }
}
