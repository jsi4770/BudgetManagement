package com.back.was.nudge;

import com.back.was.analysis.CategoryComparisonService;
import com.back.was.analysis.FakeDataPort;
import com.back.was.category.CategoryProviderImpl;
import com.back.was.common.TimeUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 계획표의 넛지 테스트 항목 (메모리 저장소로) */
class NudgeJudgeTest {

    static class MemStore implements NudgeStorePort {
        final Map<Long, NudgeRecord> rows = new HashMap<>();
        long seq = 0;

        public Optional<NudgeRecord> findThisWeek(Long userId, Long categoryId, LocalDate weekStart) {
            return rows.values().stream().filter(n -> n.userId().equals(userId)
                    && n.categoryId().equals(categoryId) && n.analysisStartDate().equals(weekStart)).findFirst();
        }

        public NudgeRecord save(NudgeRecord n) {
            NudgeRecord s = n.id() != null ? n : new NudgeRecord(++seq, n.userId(), n.categoryId(),
                    n.analysisStartDate(), n.analysisEndDate(), n.spendingAmount(), n.avgAmount(), n.growthRate(), n.status());
            rows.put(s.id(), s);
            return s;
        }

        public void delete(Long id) { rows.remove(id); }

        public void saveAiCopy(Long id, String a, String b) {}
    }

    private final LocalDate today = TimeUtil.today();
    private final List<Object> events = new ArrayList<>();

    private NudgeJudgeService judge(FakeDataPort port, MemStore store) {
        CategoryProviderImpl cats = new CategoryProviderImpl();
        return new NudgeJudgeService(new CategoryComparisonService(port, cats), port, store, cats, events::add);
    }

    @Test
    void 배달_급증이면_넛지_1개_생성_같은주_재등록은_갱신_상태유지() {
        FakeDataPort port = FakeDataPort.seed(today, true);
        MemStore store = new MemStore();
        NudgeJudgeService svc = judge(port, store);

        svc.onCreated(1L, 2L);
        assertThat(store.rows).hasSize(1);
        assertThat(store.rows.values().iterator().next().status()).isEqualTo("UNREAD");

        // 사용자가 VIEWED 로 바꾼 뒤 추가 등록 → 새로 안 만들고 값만 갱신, 상태 유지
        NudgeRecord n = store.rows.values().iterator().next();
        store.rows.put(n.id(), new NudgeRecord(n.id(), n.userId(), n.categoryId(), n.analysisStartDate(),
                n.analysisEndDate(), n.spendingAmount(), n.avgAmount(), n.growthRate(), "VIEWED"));
        port.add(2, 15_000, today, "배민");
        svc.onCreated(1L, 2L);

        assertThat(store.rows).hasSize(1);
        NudgeRecord updated = store.rows.values().iterator().next();
        assertThat(updated.status()).isEqualTo("VIEWED");
        assertThat(updated.spendingAmount()).isGreaterThan(n.spendingAmount());
    }

    @Test
    void 조건_미달이_되면_UNREAD_넛지_삭제() {
        FakeDataPort port = FakeDataPort.seed(today, true);
        MemStore store = new MemStore();
        NudgeJudgeService svc = judge(port, store);
        svc.onCreated(1L, 2L);
        assertThat(store.rows).hasSize(1);

        // 급증 거래 3건을 식비로 수정했다고 가정
        port.txs.removeIf(t -> t.date().equals(today) && t.categoryId() == 2L && t.amount() >= 28_000);
        svc.onUpdated(1L, 2L, 1L);
        assertThat(store.rows).isEmpty();
    }

    @Test
    void 가입_2주_계정은_넛지_없음() {
        FakeDataPort port = FakeDataPort.seed(today, true);
        port.signup = today.minusWeeks(2);
        MemStore store = new MemStore();
        judge(port, store).onCreated(1L, 2L);
        assertThat(store.rows).isEmpty();
    }
}
