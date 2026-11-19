package com.back.was.domain.admin;

import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 관리자 계정을 부팅 시점에 넣는다 — 시연_데이터 1장의 계정 표
 * (관리자 / {@code admin@nudge.test} / {@code Admin1234!}).
 *
 * <p><b>왜 필요한가</b>: ADMIN-01 은 {@code role: ADMIN} 토큰만 받고(명세 ADMIN-01),
 * {@code User.signUp} 은 ERD 1장에 따라 {@code USER} 를 하드코딩한다. 그래서 관리자는
 * 가입으로 만들어질 수 없고 명세도 "시드 데이터로 생성"이라고 적었다. 그 시드가 이
 * 클래스다.
 *
 * <p><b>dev 프로필 전용이다.</b> 비밀번호가 문서에 적혀 있는 계정을 운영에 띄우면 그게
 * 백도어다. 운영에서는 관리자를 <b>의도적으로</b> 만들어야 하고(수동 INSERT 나 별도
 * 운영 절차), 그때까지 ADMIN-01 은 존재하지만 아무도 통과하지 못한다 — 열려 있는 것보다
 * 안전한 기본값이다.
 *
 * <p>{@code CategorySeeder} 와 같은 모양이고 <b>없을 때만 넣는다</b> — 재기동마다 지우고
 * 다시 넣으면 ID 가 밀리고, 그 사이에 관리자로 만든 데이터의 FK 가 깨진다.
 *
 * <p>마이그레이션 도구를 도입하면 이 시드도 스크립트로 옮기는 게 맞다
 * ({@code CategorySeeder} 와 같은 이야기). 다만 <b>비밀번호는 스크립트에 넣지 말고</b>
 * 환경 변수로 받아야 한다.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    /** 시연_데이터 1장 계정 표. */
    private static final String USERNAME = "관리자";
    private static final String EMAIL = "admin@nudge.test";
    private static final String RAW_PASSWORD = "Admin1234!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(EMAIL)) {
            return;
        }

        userRepository.save(User.admin(USERNAME, EMAIL, passwordEncoder.encode(RAW_PASSWORD)));
        log.info("시연용 관리자 계정을 만들었어요: {} (dev 프로필 전용)", EMAIL);
    }
}
