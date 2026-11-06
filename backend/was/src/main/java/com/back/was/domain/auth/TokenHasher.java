package com.back.was.domain.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh Token 을 DB 조회용 SHA-256 16진 문자열로 바꾼다 (ERD 2장).
 *
 * <p>같은 입력에 같은 출력이어야 {@code WHERE TOKEN_HASH = ?} 로 행을 찾을 수 있다.
 * 비밀번호에는 절대 쓰지 말 것 — 비밀번호는 사람이 정한 짧은 값이라 bcrypt 가 필요하다.
 */
@Component
public class TokenHasher {

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 제공하도록 명세돼 있어 도달할 수 없다.
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }
}
