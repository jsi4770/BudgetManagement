package com.back.was.domain.user;

/**
 * ERD 1장 USER.ROLE.
 *
 * <p>{@code ADMIN} 은 회원가입(AUTH-01)으로 만들 수 없다 — 시드 데이터나 DB에서
 * 직접 지정한다. Access Token 의 {@code role} 클레임으로 실려 ADMIN-01 호출 때
 * DB 조회 없이 검사된다.
 */
public enum Role {
    USER,
    ADMIN
}
