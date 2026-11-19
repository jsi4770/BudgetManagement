package com.back.was.domain.dev;

import com.back.was.domain.transaction.TransactionRepository;
import com.back.was.domain.user.User;
import com.back.was.domain.user.UserRepository;
import com.back.was.domain.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시연용 더미 계정 시드. 테스트 설정은 {@code app.demo-account.enabled=false} 라 부팅 때 돌지 않는다 — 돌면 거래
 * 수백 건이 공유 H2 에 커밋돼 다른 테스트의 {@code findAll()} 단정이 깨진다. 그래서 직접 만들어 부르고, 이 테스트의
 * 트랜잭션과 함께 롤백한다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DemoAccountSeederTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserService userService;
    @Autowired
    DevSeedService devSeedService;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    ApplicationContext context;

    private DemoAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoAccountSeeder(new DemoAccountService(userRepository, passwordEncoder, userService, devSeedService));
    }

    @Test
    @DisplayName("dummy123 계정을 만들고 온보딩(천안)과 DELIVERY_SPIKE 시연 데이터까지 깐다")
    void seedsTheDemoAccount() {
        seeder.seed();

        User user = userRepository.findByEmail(DemoAccountService.EMAIL).orElseThrow();
        assertThat(user.getUsername()).isEqualTo("dummy123");
        assertThat(passwordEncoder.matches("dummy123!", user.getPasswordHash())).isTrue();
        assertThat(user.getRegionCode()).isEqualTo("44130");
        assertThat(transactionRepository.findAll())
                .filteredOn(tx -> tx.getUser().getId().equals(user.getId()))
                .isNotEmpty();
    }

    @Test
    @DisplayName("이미 있으면 건드리지 않는다 — 재기동마다 다시 깔면 시연 중에 만든 데이터가 날아간다")
    void leavesAnExistingAccountAlone() {
        seeder.seed();
        long before = transactionRepository.count();

        seeder.seed();

        assertThat(userRepository.findAll())
                .filteredOn(u -> DemoAccountService.EMAIL.equals(u.getEmail()))
                .hasSize(1);
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("테스트 설정에서는 빈이 없다 — 부팅 때 공유 H2 에 커밋하지 않는다")
    void isDisabledInTests() {
        assertThat(context.getBeanNamesForType(DemoAccountSeeder.class)).isEmpty();
    }
}
