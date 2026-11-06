package com.back.was.global.security;

/**
 * 인증된 사용자. {@code SecurityContext} 의 principal 로 들어간다.
 *
 * <p>무상태 인증이라 요청마다 DB를 조회하지 않고 Access Token 클레임만으로 만든다.
 * 그래서 토큰 발급 이후 바뀐 정보(예: 권한 강등)는 토큰이 만료될 때까지 반영되지
 * 않는다 — 명세의 6시간이 그 지연의 상한이다.
 *
 * @param id   사용자 PK. 토큰 {@code sub} 클레임
 * @param role {@code USER} / {@code ADMIN} (명세 AUTH-02)
 */
public record AuthUser(Long id, String role) {
}
