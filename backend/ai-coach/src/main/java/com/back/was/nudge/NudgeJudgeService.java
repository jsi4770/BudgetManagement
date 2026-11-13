package com.back.was.nudge;

import com.back.was.analysis.AnalysisDataPort;
import com.back.was.analysis.CategoryComparison;
import com.back.was.analysis.CategoryComparisonService;
import com.back.was.analysis.TxRecord;
import com.back.was.category.CategoryProvider;
import com.back.was.common.TimeUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 넛지 판정 (SFR-018·020, SRS 7.3-1, NDG-01). 거래 등록·수정·삭제 직후 "동기"로 호출한다.
 *
 * 규칙 (계획표)
 * - ANL-03 WEEK 와 같은 이상치 함수 (가입 4주, Z ≥ 2 AND +30,000원, 월세 제외)
 * - 조건 충족 + 이번 주 넛지 없음 → UNREAD 로 새로 생성
 * - 조건 충족 + 이미 있음 → 값만 갱신, 상태 유지 (DISMISSED 는 DISMISSED 그대로 → 다시 노출 안 됨)
 * - 조건 미달 + UNREAD 넛지 있음 → 삭제
 * - AI 문구(aiAnalysis, aiRecommendation)는 저장 후 비동기 생성 (NudgeAiCopyService)
 *
 * 호출 지점 (TransactionService 에 한 줄씩 추가)
 *   TX-01 등록 후 : nudgeJudgeService.onCreated(userId, categoryId);
 *   TX-05 수정 후 : nudgeJudgeService.onUpdated(userId, oldCategoryId, newCategoryId);
 *   TX-06 삭제 후 : nudgeJudgeService.onDeleted(userId, categoryId);
 */
@Service
public class NudgeJudgeService {

    private static final int MEMO_LIMIT = 6;

    private final CategoryComparisonService comparison;
    private final AnalysisDataPort dataPort;
    private final NudgeStorePort store;
    private final CategoryProvider categories;
    private final ApplicationEventPublisher events;

    public NudgeJudgeService(CategoryComparisonService comparison, AnalysisDataPort dataPort,
                             NudgeStorePort store, CategoryProvider categories,
                             ApplicationEventPublisher events) {
        this.comparison = comparison;
        this.dataPort = dataPort;
        this.store = store;
        this.categories = categories;
        this.events = events;
    }

    public void onCreated(Long userId, Long categoryId) {
        rejudge(userId, Arrays.asList(categoryId));
    }

    /** 카테고리가 바뀌었으면 이전·새 카테고리 모두 재판정 */
    public void onUpdated(Long userId, Long oldCategoryId, Long newCategoryId) {
        rejudge(userId, Arrays.asList(oldCategoryId, newCategoryId));
    }

    public void onDeleted(Long userId, Long categoryId) {
        rejudge(userId, Arrays.asList(categoryId));
    }

    public void rejudge(Long userId, Collection<Long> categoryIds) {
        LocalDate today = TimeUtil.today();
        LocalDate weekStart = TimeUtil.mondayOf(today);
        CategoryComparison week = comparison.compareWeek(userId, today);

        for (Long catId : new LinkedHashSet<>(categoryIds)) {
            if (catId == null || !categories.isExpense(catId) || categories.isRent(catId)) continue;

            Optional<CategoryComparison.Item> item = comparison.weekItem(week, catId);
            boolean anomaly = week.dataSufficient() && item.map(CategoryComparison.Item::isAnomaly).orElse(false);
            Optional<NudgeRecord> existing = store.findThisWeek(userId, catId, weekStart);

            if (anomaly) {
                CategoryComparison.Item it = item.get();
                long avg = it.averageAmount() == null ? 0 : it.averageAmount();

                NudgeRecord saved = existing
                        .map(n -> store.save(n.withValues(today, it.amount(), avg, it.growthRate())))
                        .orElseGet(() -> store.save(new NudgeRecord(null, userId, catId, weekStart, today,
                                it.amount(), avg, it.growthRate(), NudgeRecord.UNREAD)));

                // 닫은 넛지는 어차피 안 보이니 문구 생성 생략 (Gemini 한도 절약)
                if (!NudgeRecord.DISMISSED.equals(saved.status())) {
                    events.publishEvent(new NudgeCopyRequested(saved.id(),
                            facts(userId, catId, weekStart, today, it.amount(), avg, it.growthRate())));
                }
            } else if (existing.isPresent() && NudgeRecord.UNREAD.equals(existing.get().status())) {
                store.delete(existing.get().id());
            }
        }
    }

    /** AI 문구용 사실값 계산 */
    private NudgeFacts facts(Long userId, Long catId, LocalDate weekStart, LocalDate today,
                             long spending, long avg, Double growth) {
        List<TxRecord> txs = dataPort.findTransactions(userId, weekStart, today).stream()
                .filter(t -> catId.equals(t.categoryId()))
                .toList();
        int count = txs.size();
        int weekend = (int) txs.stream()
                .filter(t -> t.date().getDayOfWeek() == DayOfWeek.SATURDAY
                        || t.date().getDayOfWeek() == DayOfWeek.SUNDAY)
                .count();
        List<String> memos = txs.stream().map(TxRecord::memo).filter(Objects::nonNull)
                .filter(m -> !m.isBlank()).distinct().limit(MEMO_LIMIT).toList();
        String name = categories.findById(catId).map(c -> c.name()).orElse("");

        return new NudgeFacts(name, (int) ChronoUnit.DAYS.between(weekStart, today) + 1, count,
                spending, avg, spending - avg, growth, count == 0 ? 0 : Math.round((double) spending / count),
                weekend, memos);
    }
}
