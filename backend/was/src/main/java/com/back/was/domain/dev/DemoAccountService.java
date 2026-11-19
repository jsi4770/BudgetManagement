package com.back.was.domain.dev;

import com.back.was.domain.dev.dto.SeedRequest;
import com.back.was.domain.dev.dto.SeedResponse;
import com.back.was.domain.user.Gender;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.domain.user.UserService;
import com.back.was.domain.user.dto.OnboardingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 계정 상태 — 온보딩(천안·25세·선택 안 함) + DEV-01 {@code DELIVERY_SPIKE}. "시연 계정처럼"을 한 곳에서 정의해
 * 부팅 때 만드는 시연 계정({@code dummy123@dummy123.com} / {@code dummy123!}, {@link DemoAccountSeeder})과
 * DEV-03(로그인한 계정을 같은 상태로)이 어긋나지 않게 한다.
 *
 * <p><b>dev 프로필 전용</b> — {@code AdminSeeder} 와 같은 이유. 비밀번호가 알려진 계정이다.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DemoAccountService {

    static final String EMAIL = "dummy123@dummy123.com";
    private static final String USERNAME = "dummy123";
    private static final String RAW_PASSWORD = "dummy123!";
    private static final DemoScenario SCENARIO = DemoScenario.DELIVERY_SPIKE;

    /** 천안시 — 청년정책 롤링이 보이는 지원 지역. 나이는 청년정책 대상 범위 안. */
    private static final OnboardingRequest ONBOARDING = new OnboardingRequest("44130", 25, Gender.NONE);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final DevSeedService devSeedService;

    /** 시연 계정이 없을 때만 만든다. 있으면 시연 중에 만든 데이터를 지키려고 건드리지 않는다. */
    @Transactional
    public void createIfMissing() {
        if (userRepository.existsByEmail(EMAIL)) {
            return;
        }
        User user = userRepository.save(User.signUp(USERNAME, EMAIL, passwordEncoder.encode(RAW_PASSWORD)));
        resetAsDemo(user.getId());
        log.info("시연용 더미 계정을 만들었어요: {} (dev 프로필 전용)", EMAIL);
    }

    /**
     * DEV-03: 이 사용자를 시연 계정과 같은 상태로. 온보딩을 덮어쓰고 DEV-01 {@code DELIVERY_SPIKE} 를 오늘 기준으로
     * 다시 깐다(거래·예산·넛지·코칭·챌린지·리포트 삭제 후). 닉네임·비밀번호는 건드리지 않는다.
     */
    @Transactional
    public SeedResponse resetAsDemo(Long userId) {
        userService.completeOnboarding(userId, ONBOARDING);
        return devSeedService.seed(userId, new SeedRequest(SCENARIO));
    }
}
