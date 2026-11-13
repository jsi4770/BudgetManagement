package com.back.was.nudge;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 단독 실행용 메모리 구현. 팀 DB 연결 시 NUDGE 테이블로 교체 */
@Component
public class NudgeStorePortAdapter implements NudgeStorePort {

    private final Map<Long, NudgeRecord> nudges = new ConcurrentHashMap<>();
    private final Map<Long, String[]> aiCopy = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public Optional<NudgeRecord> findThisWeek(Long userId, Long categoryId, LocalDate weekStart) {
        return nudges.values().stream().filter(n -> n.userId().equals(userId)
                && n.categoryId().equals(categoryId) && n.analysisStartDate().equals(weekStart)).findFirst();
    }

    @Override
    public NudgeRecord save(NudgeRecord n) {
        Long id = n.id() != null ? n.id() : seq.incrementAndGet();
        NudgeRecord saved = new NudgeRecord(id, n.userId(), n.categoryId(), n.analysisStartDate(), n.analysisEndDate(),
                n.spendingAmount(), n.avgAmount(), n.growthRate(), n.status());
        nudges.put(id, saved);
        return saved;
    }

    @Override
    public void delete(Long nudgeId) { nudges.remove(nudgeId); aiCopy.remove(nudgeId); }

    @Override
    public void saveAiCopy(Long nudgeId, String aiAnalysis, String aiRecommendation) {
        aiCopy.put(nudgeId, new String[]{aiAnalysis, aiRecommendation});
    }

    public Optional<String[]> aiCopyOf(Long nudgeId) { return Optional.ofNullable(aiCopy.get(nudgeId)); }
}
