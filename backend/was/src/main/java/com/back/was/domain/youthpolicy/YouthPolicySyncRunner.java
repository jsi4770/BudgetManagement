package com.back.was.domain.youthpolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 부팅 때 정책 테이블이 비어 있으면 한 번 채운다({@code YOUTH_POLICY_SYNC_ON_START=true}). 별도 스레드라 기동을 막지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "youth-policy.sync-on-start", havingValue = "true")
public class YouthPolicySyncRunner implements ApplicationRunner {

    private final YouthPolicySyncService syncService;
    private final YouthPolicyRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("youth-policy-initial-sync").start(() -> {
            try {
                if (repository.count() == 0) {
                    syncService.sync();
                }
            } catch (RuntimeException e) {
                log.error("청년정책 초기 동기화 실패: {}", e.getMessage());
            }
        });
    }
}
