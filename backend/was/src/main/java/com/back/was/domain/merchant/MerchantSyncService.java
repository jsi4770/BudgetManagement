package com.back.was.domain.merchant;

import com.back.was.domain.merchant.MerchantApiClient.Franchise;
import com.back.was.domain.merchant.MerchantApiClient.FranchisePage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 가맹점 API → {@code local_merchants} 적재.
 *
 * <p>페이지마다 따로 커밋한다. 천안은 약 1만 건, 아산은 약 3.8만 건이라 한 트랜잭션으로 묶으면 너무
 * 길고, 중간에 실패해도 앞 페이지는 남는다. 정리(이번에 안 보인 행 삭제)는 <b>끝까지 읽은 경우에만</b>
 * 한다 — 중간에 끊긴 동기화가 멀쩡한 행을 지우면 안 된다.
 *
 * <p>한 번에 하나만 돈다. 이미 도는 중이면 두 번째 호출은 아무것도 하지 않고 {@code -1} 을 돌려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantSyncService {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final MerchantApiClient client;
    private final LocalMerchantRepository repository;
    private final TransactionTemplate tx;
    private final MerchantProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** @return 저장·갱신한 건수. 키가 없으면 0, 이미 도는 중이면 -1 */
    public int sync(SupportedRegion region) {
        if (!props.hasKey()) {
            log.warn("MERCHANT_API_KEY 가 없어 가맹점 동기화를 건너뜁니다 ({})", region.displayName());
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("가맹점 동기화가 이미 진행 중이라 {} 요청을 건너뜁니다", region.displayName());
            return -1;
        }
        try {
            return doSync(region);
        } finally {
            running.set(false);
        }
    }

    private int doSync(SupportedRegion region) {
        LocalDateTime startedAt = LocalDateTime.now();
        int perPage = props.perPage();
        int saved = 0;
        long seen = 0;
        long matchCount;

        int page = 1;
        do {
            FranchisePage result = client.fetch(region.code(), page, perPage);
            matchCount = result.matchCount();
            List<Franchise> items = result.items();
            seen += perPage;   // 건너뛴 행이 있어도 "읽은 위치"는 페이지 단위로 센다
            saved += tx.execute(status -> upsert(region, items, startedAt));
            page++;
        } while (seen < matchCount);

        int removed = 0;
        if (saved > 0) {
            removed = tx.execute(status -> repository.deleteStale(region.code(), startedAt));
        }
        log.info("가맹점 동기화 완료 {} — 저장·갱신 {}건, 정리 {}건 (API 매칭 {}건)",
                region.displayName(), saved, removed, matchCount);
        return saved;
    }

    private int upsert(SupportedRegion region, List<Franchise> items, LocalDateTime syncedAt) {
        // 같은 페이지 안의 중복(같은 altText)은 뒤의 것이 이긴다.
        Map<String, Franchise> byKey = new LinkedHashMap<>();
        for (Franchise f : items) {
            if (!f.isTerminated()) {
                byKey.put(f.altText(), f);
            }
        }
        if (byKey.isEmpty()) {
            return 0;
        }
        Map<String, LocalMerchant> existing = repository.findByAltTextIn(byKey.keySet()).stream()
                .collect(Collectors.toMap(LocalMerchant::getAltText, m -> m));

        List<LocalMerchant> toSave = byKey.values().stream().map(f -> {
            LocalMerchant m = existing.getOrDefault(f.altText(), LocalMerchant.create(f.altText(), region.code()));
            m.refresh(f.name(), f.emdName(), f.address(), f.phone(), f.lat(), f.lng(),
                    f.ksicCode(), f.ksicName(), MerchantClassifier.classify(f.ksicCode(), f.name()),
                    parseDate(f.baseDate()), syncedAt);
            return m;
        }).toList();
        repository.saveAll(toSave);
        return toSave.size();
    }

    private static LocalDate parseDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(yyyymmdd.strip(), BASIC_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
