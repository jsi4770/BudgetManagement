package com.back.was.domain.merchant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 부팅 때 가맹점 테이블이 비어 있는 지역만 CSV 로 한 번 채운다 ({@code MERCHANT_SYNC_ON_START=true} 일 때만).
 *
 * <p>배포 환경에는 관리자 계정이 없을 수 있어서 관리자 API 만으로는 첫 적재를 못 한다. 그래서 환경변수
 * 하나로 켜게 했다. 1.3만 건 안팎이라 <b>별도 스레드</b>에서 돌려 서버 기동을
 * 막지 않는다 — 그동안 목록 API 는 빈 결과를 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "merchant.sync-on-start", havingValue = "true")
public class MerchantSyncRunner implements ApplicationRunner {

    private final MerchantFileImportService importService;
    private final LocalMerchantRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("merchant-initial-sync").start(() -> {
            for (SupportedRegion region : SupportedRegion.values()) {
                try {
                    if (repository.countByRegionCode(region.code()) == 0) {
                        importService.importRegion(region);
                    }
                } catch (RuntimeException e) {
                    // 메시지에 URL(=키)이 없도록 클라이언트가 이미 정리해서 던진다.
                    log.error("{} 가맹점 초기 동기화 실패: {}", region.displayName(), e.getMessage());
                }
            }
        });
    }
}
