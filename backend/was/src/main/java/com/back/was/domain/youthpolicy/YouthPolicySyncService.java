package com.back.was.domain.youthpolicy;

import com.back.was.domain.merchant.SupportedRegion;
import com.back.was.domain.youthpolicy.YouthPolicyApiClient.Policy;
import com.back.was.domain.youthpolicy.YouthPolicyApiClient.PolicyPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 청년정책 API → {@code youth_policies} 적재. 지원 지역(천안·아산) 각각으로 호출하고 정책번호로 합친다
 * (두 지역이 함께 대상인 정책은 한 번만 저장). 전부 끝까지 읽었을 때만 이번에 안 보인 정책을 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouthPolicySyncService {

    private final YouthPolicyApiClient client;
    private final YouthPolicyRepository repository;
    private final TransactionTemplate tx;
    private final YouthPolicyProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** @return 저장·갱신한 건수. 키가 없으면 0, 이미 도는 중이면 -1 */
    public int sync() {
        if (!props.hasKey()) {
            log.warn("YOUTH_POLICY_API_KEY 가 없어 청년정책 동기화를 건너뜁니다");
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            return -1;
        }
        try {
            LocalDateTime startedAt = LocalDateTime.now();
            Map<String, Policy> all = new LinkedHashMap<>();
            for (SupportedRegion region : SupportedRegion.values()) {
                int page = 1;
                long seen = 0;
                long total;
                do {
                    PolicyPage result = client.fetch(region.code(), page, props.pageSize());
                    total = result.totalCount();
                    result.items().forEach(p -> all.put(p.plcyNo(), p));
                    seen += props.pageSize();
                    page++;
                } while (seen < total);
            }
            int saved = tx.execute(status -> upsert(all, startedAt));
            int removed = saved > 0 ? tx.execute(status -> repository.deleteStale(startedAt)) : 0;
            log.info("청년정책 동기화 완료 — 저장·갱신 {}건, 정리 {}건", saved, removed);
            return saved;
        } finally {
            running.set(false);
        }
    }

    private int upsert(Map<String, Policy> all, LocalDateTime syncedAt) {
        Map<String, YouthPolicy> existing = repository.findByPlcyNoIn(all.keySet()).stream()
                .collect(Collectors.toMap(YouthPolicy::getPlcyNo, p -> p));
        var toSave = all.values().stream().map(p -> {
            YouthPolicy e = existing.getOrDefault(p.plcyNo(), YouthPolicy.create(p.plcyNo()));
            e.refresh(p, syncedAt);
            return e;
        }).toList();
        repository.saveAll(toSave);
        return toSave.size();
    }
}
