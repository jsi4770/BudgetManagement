package com.back.was.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** AUTH-03 재발급·AUTH-04 로그아웃이 쿠키의 토큰으로 행을 찾을 때 쓴다. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    /**
     * ERD 2장: 일일 스케줄러가 만료된 행을 지운다 ({@link ExpiredRefreshTokenCleanupJob}).
     *
     * <p>파생 삭제 쿼리는 <b>호출자가 트랜잭션을 열어 줘야</b> 한다 — 없으면
     * {@code InvalidDataAccessApiUsageException} 이다.
     */
    long deleteByExpiresAtBefore(LocalDateTime threshold);
}
