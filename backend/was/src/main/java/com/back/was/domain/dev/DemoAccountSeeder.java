package com.back.was.domain.dev;

import com.back.was.domain.dev.dto.SeedRequest;
import com.back.was.domain.user.Gender;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.domain.user.UserService;
import com.back.was.domain.user.dto.OnboardingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 더미 계정({@code dummy123@dummy123.com} / {@code dummy123!})을 부팅 때 만들고 DEV-01 의
 * {@code DELIVERY_SPIKE} 데이터를 깐다. 가입·온보딩·시드 버튼을 매번 누르지 않고 바로 로그인해 보여 주기 위한 것이다.
 *
 * <ul>
 *   <li><b>dev 프로필 전용</b> — {@code AdminSeeder} 와 같은 이유. 비밀번호가 알려진 계정이다.</li>
 *   <li><b>없을 때만</b> 만든다 — 재기동마다 다시 깔면 시연 중에 만든 거래·챌린지가 날아간다. 데이터를 오늘 기준으로
 *       새로 깔려면 앱의 시연 데이터 버튼(DEV-01)을 누른다.</li>
 *   <li>{@link ApplicationReadyEvent} 에서 돈다 — {@code ApplicationRunner} 끼리는 순서가 없어서 카테고리 시드보다
 *       먼저 돌 수 있다.</li>
 *   <li>테스트 설정은 {@code app.demo-account.enabled=false} — 부팅 때 공유 H2 에 거래 수백 건을 커밋하지 않게.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo-account.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoAccountSeeder {

    static final String EMAIL = "dummy123@dummy123.com";
    private static final String USERNAME = "dummy123";
    private static final String RAW_PASSWORD = "dummy123!";

    /** 천안시 — 청년정책 롤링이 보이는 지원 지역. 나이는 청년정책 대상 범위 안. */
    private static final OnboardingRequest ONBOARDING = new OnboardingRequest("44130", 25, Gender.NONE);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final DevSeedService devSeedService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (userRepository.existsByEmail(EMAIL)) {
            return;
        }

        User user = userRepository.save(User.signUp(USERNAME, EMAIL, passwordEncoder.encode(RAW_PASSWORD)));
        userService.completeOnboarding(user.getId(), ONBOARDING);
        devSeedService.seed(user.getId(), new SeedRequest(DemoScenario.DELIVERY_SPIKE));
        log.info("시연용 더미 계정을 만들었어요: {} (dev 프로필 전용)", EMAIL);
    }
}
