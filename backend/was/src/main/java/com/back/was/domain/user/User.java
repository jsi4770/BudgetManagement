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

    /**
     * 사는 지역(법정동코드 앞 5자리, 천안 44130·아산 44200). 지역 가맹점 목록과 코칭 추천의 기준이다.
     * 가입 때는 묻지 않으므로 {@code null} 일 수 있다 — USER-02 로 나중에 설정한다.
     */
    @Column(name = "region_code", length = 5)
    private String regionCode;

    /** 출생연도. 나이는 여기서 계산한다(해마다 틀어지는 나이를 저장하지 않는다). 온보딩 전에는 {@code null}. */
    @Column(name = "birth_year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

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

    /**
     * 관리자 계정 — <b>시드 전용</b>이다 (명세 ADMIN-01 "관리자 계정은 시드 데이터로 생성").
     *
     * <p>ERD 1장이 금지한 것은 <b>가입 경로로</b> ADMIN 을 만드는 것이고, 그래서
     * {@link #signUp} 이 역할을 하드코딩한다. 관리자는 어딘가에서 만들어져야 하므로 그
     * 경로를 이렇게 하나만, 이름으로 드러나게 열어 둔다.
     *
     * <p><b>호출자는 {@code AdminSeeder} 하나여야 한다.</b> 요청을 처리하는 코드에서 이
     * 메서드를 부르면 ERD 1장의 금지가 무의미해진다 — 리뷰에서 그 호출을 막는 것이 유일한
     * 방어다. 그 시더는 dev 프로필 전용이므로 운영에는 관리자가 자동으로 생기지 않는다.
     */
    public static User admin(String username, String email, String passwordHash) {
        return new User(username, email, passwordHash, Role.ADMIN);
    }

    public void changeUsername(String username) {
        this.username = username;
    }

    /** 지원 지역인지는 요청 DTO 의 검증이 보장한다. */
    public void changeRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public void changeBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public void changeGender(Gender gender) {
        this.gender = gender;
    }

    /** 만 나이 근사(올해 − 출생연도). 모르면 {@code null}. */
    public Integer ageIn(int year) {
        return birthYear == null ? null : year - birthYear;
    }

    /** 지역·나이·성별을 모두 정했는지 — 온보딩 화면을 건너뛰어도 되는지의 기준. */
    public boolean isOnboarded() {
        return regionCode != null && birthYear != null && gender != null;
    }
}
