package com.back.was.domain.nudge;

import com.back.was.domain.analysis.AnalysisPeriod;
import com.back.was.domain.analysis.SpendingComparison;
import com.back.was.domain.analysis.SpendingSampler;
import com.back.was.domain.analysis.SpendingSamples;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryType;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.user.User;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 넛지 판정 (서버_내부_처리 3장 = ERD 6장 "넛지 판정 규칙").
 *
 * <p>거래가 등록·수정·삭제될 때 {@code TransactionService} 가 <b>같은 트랜잭션 안에서
 * 동기로</b> 부른다(서버_내부_처리 1장 순서 3번). 이벤트 리스너가 아닌 이유도 거기 있다 —
 * 거래 저장과 넛지가 한 커밋에 묶여야 "저장은 됐는데 카드만 안 생긴" 상태가 없다.
 *
 * <p>카드를 만들거나 갱신하면 {@link NudgeJudgedEvent} 를 쏜다 — 후처리 5번(AI 문구)이
 * 커밋 후 비동기로 받는다.
 *
 * <p>평균·Z 는 {@link SpendingComparison} 이, 표본 수집은 {@link SpendingSampler} 가 한다.
 * ANL-03 {@code WEEK} 과 <b>같은 두 컴포넌트</b>를 쓰는 것이 핵심이다(서버_내부_처리 3장
 * "같은 계산 함수 사용 → 넛지 평균과 분석 화면 평균이 항상 일치"). 여기서 집계를 따로
 * 구현하면 카드의 "평소 이맘때 평균"과 분석 화면의 평균이 조용히 달라진다.
 *
 * <p>판정 기간은 <b>언제나 이번 주</b>다. 그래서 3주 전 거래를 고쳐도 판정을 다시 돌려야
 * 한다 — 그 거래는 직전 4주 표본에 들어가 평균과 Z 를 바꾼다. 호출자가 거래 날짜로
 * 걸러내면 안 되는 이유다.
 */
@Component
@RequiredArgsConstructor
public class NudgeJudgement {

    /** 넛지는 주간 기준이다 (ERD 6장 — 직전 4주, 월요일 시작). */
    private static final AnalysisPeriod PERIOD = AnalysisPeriod.WEEK;

    private final NudgeRepository nudgeRepository;
    private final SpendingSampler spendingSampler;
    private final ApplicationEventPublisher events;

    /**
     * 주어진 카테고리들의 이번 주 넛지를 다시 판정한다.
     *
     * <p>TX-05 는 수정 전·후 카테고리를 둘 다 넘긴다(서버_내부_처리 1장). 카테고리가 바뀌지
     * 않아 같은 값이 두 번 들어오면 <b>한 번만</b> 판정한다 — 호출자가 비교할 필요가 없게
     * 여기서 ID 로 중복을 없앤다.
     *
     * <p>표본 조회는 카테고리 수와 무관하게 구간 수(이번 주 + 4주)만큼이다. 카테고리별로
     * 따로 돌면 거래 한 건마다 쿼리가 두 배가 된다.
     */
    public void judge(User user, Category... categories) {
        Map<Long, Category> targets = new LinkedHashMap<>();
        for (Category category : categories) {
            if (isJudgeable(category)) {
                targets.putIfAbsent(category.getId(), category);
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        LocalDate today = KstTime.today();
        if (!hasEnoughHistory(user, today)) {
            return;
        }

        DateRange analysisPeriod = PERIOD.analysisRange(today);
        SpendingSamples spending = spendingSampler.collect(user.getId(), PERIOD, today, RentCategory.ID);

        targets.values().forEach(category -> judgeOne(user, category, analysisPeriod, spending));
    }

    /**
     * 판정 대상인지 — <b>지출 카테고리이고 월세가 아닐 때</b>만이다
     * (ERD 6장 "월세 제외, 수입 제외").
     *
     * <p>수입이 섞이면 "평소보다 많이 벌었어요" 카드가 생기고, 월세는 한 달에 한 번 큰
     * 금액이라 주간 표본을 흔든다. 표본 쿼리에서도 월세를 빼지만
     * ({@link RentCategory#ID}) 대상 판단을 여기서 한 번 더 하는 쪽이 읽기 쉽다.
     */
    private static boolean isJudgeable(Category category) {
        return category != null
                && category.getType() == CategoryType.EXPENSE
                && !RentCategory.isRent(category.getId());
    }

    /**
     * ERD 6장 "판정 생략 ① 가입일이 표본 첫 주 월요일 이후" — 4주치 기록이 아직 없으면
     * 비교가 성립하지 않는다. ANL-03 의 {@code dataSufficient} 와 같은 식이다.
     */
    private static boolean hasEnoughHistory(User user, LocalDate today) {
        return !user.getCreatedAt().toLocalDate().isAfter(PERIOD.oldestSampleStart(today));
    }

    private void judgeOne(User user, Category category, DateRange analysisPeriod,
                          SpendingSamples spending) {
        SpendingComparison comparison = spending.compare(category.getId());
        Optional<Nudge> existing = nudgeRepository.findByUserIdAndCategoryIdAndAnalysisStartDate(
                user.getId(), category.getId(), analysisPeriod.start());

        // ERD 6장 "판정 생략 ② 표준편차 0" — 표본이 전부 같은 값이면 Z 를 낼 수 없다.
        // 생략은 생략이다: 기존 카드를 지우지도, 갱신하지도 않는다. 조건을 평가조차 하지
        // 못한 상황과 "조건 미달로 바뀜"은 다르다.
        if (comparison.zScore() == null) {
            return;
        }

        if (comparison.isAnomaly()) {
            Nudge nudge = existing
                    .map(found -> {
                        found.refresh(analysisPeriod.end(), comparison);
                        return found;
                    })
                    .orElseGet(() -> nudgeRepository.save(
                            Nudge.of(user, category, analysisPeriod, comparison)));

            // 후처리 5번: 커밋 후 비동기로 AI 문구를 채운다 (서버_내부_처리 1장·8장).
            // 갱신일 때도 쏜다 — 금액이 바뀌었으면 문구도 다시 생성해야 한다(ERD 6장).
            events.publishEvent(new NudgeJudgedEvent(nudge.getId()));
            return;
        }

        // 조건 미달로 바뀜 (ERD 6장): UNREAD 면 삭제, 이미 반응한 카드는 유지한다 —
        // 사용자가 보고 닫거나 코칭까지 시작한 카드를 서버가 치우면 이력이 사라진다.
        existing.filter(nudge -> nudge.getStatus() == NudgeStatus.UNREAD)
                .ifPresent(nudgeRepository::delete);
    }
}
