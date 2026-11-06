package com.back.was.domain.auth;

import com.back.was.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * ERD 2장 REFRESH_TOKEN. 로그인한 기기마다 한 줄.
 *
 * <p><b>토큰 원문은 저장하지 않는다</b> — 브라우저의 httpOnly 쿠키에만 있고 DB에는
 * SHA-256 해시만 둔다. DB가 유출돼도 해시로는 토큰을 만들 수 없다.
 *
 * <p>bcrypt 가 아니라 SHA-256 인 이유는 ERD 2장에 적힌 대로다: bcrypt 는 같은 값도
 * 매번 다른 해시를 만들어 {@code WHERE TOKEN_HASH = ?} 로 행을 찾을 수 없다.
 * Refresh Token 은 서명된 충분히 긴 값이라 SHA-256 으로도 안전하다.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 16진 문자열(64자). */
    @Column(name = "token_hash", nullable = false, length = 255, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(user, tokenHash, expiresAt);
    }
}
