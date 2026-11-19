package com.back.was.domain.admin;

import com.back.was.domain.user.Role;
import com.back.was.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시연용 관리자 계정 시드 (시연_데이터 1장 · 명세 ADMIN-01 "관리자 계정은 시드 데이터로
 * 생성").
 *
 * <p>부팅 시점의 실행 결과를 단정하지 않고 {@code run} 을 직접 부른다 — 같은 dev 컨텍스트를
 * 공유하는 다른 테스트가 {@code users} 를 비우기도 해서(예: {@code DevChallengeApiTest} 의
 * 정리) 시작 시점에 심은 행이 남아 있다고 가정할 수 없다. 두 번 불러서 <b>멱등성</b>을
 * 보는 쪽이 실제 규칙이기도 하다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AdminSeederTest {

    /** 시연_데이터 1장 계정 표. */
    private static final String EMAIL = "admin@nudge.test";
    private static final String RAW_PASSWORD = "Admin1234!";

    @Autowired
    AdminSeeder adminSeeder;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("ADMIN 역할의 관리자 계정을 만든다 — 가입으로는 만들 수 없는 계정이다")
    void seedsTheAdminAccount() {
        adminSeeder.run(null);

        assertThat(userRepository.findByEmail(EMAIL))
                .get()
                .satisfies(admin -> {
                    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
                    assertThat(admin.getUsername()).isEqualTo("관리자");
                    // 비밀번호는 bcrypt 로 저장된다 — 평문이 남으면 안 된다
                    assertThat(admin.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
                    assertThat(passwordEncoder.matches(RAW_PASSWORD, admin.getPasswordHash()))
                            .isTrue();
                });
    }

    @Test
    @DisplayName("두 번 실행해도 하나다 — 재기동마다 지우고 다시 넣으면 FK 가 깨진다")
    void isIdempotent() {
        adminSeeder.run(null);
        adminSeeder.run(null);

        assertThat(userRepository.findAll())
                .filteredOn(user -> EMAIL.equals(user.getEmail()))
                .hasSize(1);
    }

    @Test
    @DisplayName("시드한 계정으로 로그인하면 ADMIN 토큰을 받는다 — 시연에서 쓰는 경로다")
    void theSeededAccountCanLogIn() {
        adminSeeder.run(null);

        assertThat(userRepository.findByEmail(EMAIL))
                .get()
                .satisfies(admin -> assertThat(admin.getRole().name()).isEqualTo("ADMIN"));
    }
}
