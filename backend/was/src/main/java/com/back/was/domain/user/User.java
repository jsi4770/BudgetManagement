package com.back.was.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ERD 1장 USER.
 *
 * <p>테이블명이 {@code users} 인 것은 ERD 0장 주석대로 {@code USER} 가 DB 예약어와
 * 겹치기 때문이다.
 *
 * <p>{@code CREATED_AT} 은 가입일이자 넛지·월간 분석의 "최소 데이터" 판단 기준이므로
 * (ERD 1장) 수정되지 않아야 한다 — {@code updatable = false}.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    /** bcrypt 결과(60자). 원문 비밀번호는 어디에도 저장하지 않는다. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private User(String username, String email, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * AUTH-01 회원가입. ERD 1장에 따라 role 은 항상 {@link Role#USER} 다 —
     * 가입 경로로 ADMIN 을 만들 수 없게 생성자를 열어두지 않는다.
     */
    public static User signUp(String username, String email, String passwordHash) {
        return new User(username, email, passwordHash, Role.USER);
    }

    public void changeUsername(String username) {
        this.username = username;
    }
}
