package com.back.was.domain.dev;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 부팅 때 시연 계정({@link DemoAccountService})이 <b>없을 때만</b> 만든다 — 재기동마다 다시 깔면 시연 중에 만든
 * 거래·챌린지가 날아간다. 오늘 기준으로 다시 깔려면 DEV-03 을 부른다.
 *
 * <ul>
 *   <li>{@link ApplicationReadyEvent} 에서 돈다 — {@code ApplicationRunner} 끼리는 순서가 없어서 카테고리 시드보다
 *       먼저 돌 수 있다.</li>
 *   <li>테스트 설정은 {@code app.demo-account.enabled=false} — 부팅 때 공유 H2 에 거래 수백 건을 커밋하지 않게.</li>
 * </ul>
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.demo-account.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoAccountSeeder {

    private final DemoAccountService demoAccountService;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        demoAccountService.createIfMissing();
    }
}
