package com.back.was.domain.coaching;

import com.back.was.domain.ai.ProposedGoal;
import com.back.was.domain.analysis.SpendingSampler;
import com.back.was.domain.budget.Budget;
import com.back.was.domain.budget.BudgetRepository;
import com.back.was.domain.budget.SpendingFocus;
import com.back.was.domain.category.Category;
import com.back.was.domain.category.CategoryRepository;
import com.back.was.domain.category.RentCategory;
import com.back.was.domain.nudge.NudgeRepository;
import com.back.was.domain.nudge.NudgeStatus;
import com.back.was.domain.user.User;
import com.back.was.global.support.DateRange;
import com.back.was.global.support.KstTime;
import com.back.was.global.support.Weeks;
import com.back.was.global.support.YearMonths;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Gemini 가 목표를 제안하지 못했을 때 <b>서버가 규칙으로</b> 만드는 제안 —
 * {@code docs/AI_대체_문구.md} <b>5장</b>.
 *
 * <p>목적이 분명하다: "시연 중에 Gemini 가 막혀도 넛지 → 코칭 → 챌린지 확정 흐름을 끝까지
 * 보여 줄 수 있어요"(5장 각주). Gemini 가 키 없음·한도·장애로 실패하면 <b>COACH-03 의 제안이
 * 여기서 나온다</b>(정상일 때는 Gemini 의 구조화 출력이 쓰인다).
 *
 * <p>결과 타입이 {@link ProposedGoal}(AI 패키지의 레코드)인 것은 의도다 — Gemini 가
 * 돌려줬을 모양과 같아서, 저장·검증·응답 변환 코드가 두 경로에 하나씩 생기지 않는다.
 *
 * <h2>5장의 규칙</h2>
 * <table border="1">
 *   <caption>AI_대체_문구 5장</caption>
 *   <tr><th>항목</th><th>규칙</th></tr>
 *   <tr><td>카테고리</td><td>① 넛지에서 시작했으면 넛지 카테고리 ② 아니면 소비 관리 목표
 *       ({@code DELIVERY} → 배달, {@code CAFE} → 카페) ③ {@code FIXED_COST} 이거나 목표가
 *       없으면 직전 4주 지출이 가장 큰 카테고리 (월세 제외)</td></tr>
 *   <tr><td>목표 유형</td><td>{@code AMOUNT}</td></tr>
 *   <tr><td>목표 금액</td><td>직전 4주 주간 평균의 80% 를 만 원 단위로 내림 (최소 10,000원)</td></tr>
 *   <tr><td>기간</td><td>7일</td></tr>
 *   <tr><td>제목</td><td>6장 템플릿 ({@link GoalTitle})</td></tr>
 * </table>
 *
 * <h2>문서가 정하지 않아 여기서 정한 두 가지</h2>
 * <ul>
 *   <li><b>①의 "넛지에서 시작했으면"을 어떻게 아는가.</b> ERD 7장이 COACHING 과 NUDGE
 *       사이에 FK 를 두지 않았으므로(넛지는 첫 메시지 맥락으로만 쓰인다) COACH-03 시점에
 *       저장된 연결이 없다. 그래서 <b>이번 주의 {@code COACHING_STARTED} 넛지</b>로
 *       추론한다 — COACH-01 이 {@code nudgeId} 를 받으면 그 상태로 바꿔 두기 때문에 사실상
 *       같은 신호다. 추론이므로 틀릴 수 있고(NDG-03 으로 직접 그 상태를 보낸 경우), 틀리면
 *       ③과 같은 카테고리가 나올 가능성이 높다 — 넛지는 지출이 튄 카테고리에 생긴다.</li>
 *   <li><b>"직전 4주 주간 평균"의 주 자르기.</b> {@link Weeks#fullWeeksBefore} 로
 *       <b>완전한 4주</b>를 쓴다. 넛지·ANL-03 의 표본({@code elapsedWeeksAgo}, 주당 k일)을
 *       쓰면 월요일에 호출할 때 하루치 평균이 나와 7일 목표가 터무니없이 작아진다. 5장의
 *       예시 숫자(60,000원)는 시연 6-1 의 넛지 평균과 같지만, 7일 목표의 기준은 7일치
 *       기록이어야 한다는 쪽을 택했다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RuleBasedGoalProposer {

    /** 5장 "직전 4주". */
    private static final int SAMPLE_WEEKS = 4;

    /** 5장 "기간: 7일". */
    private static final int PERIOD_DAYS = 7;

    /** 5장 "주간 평균의 80%". */
    private static final int GOAL_PERCENT = 80;

    /** 5장 "만 원 단위로 내림". */
    private static final int GOAL_UNIT = 10_000;

    /** 5장 "최소 10,000원". */
    private static final int MIN_GOAL = 10_000;

    private final SpendingSampler spendingSampler;
    private final NudgeRepository nudgeRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    /**
     * @return 5장 규칙으로 만든 제안. <b>카테고리를 고를 근거가 하나도 없으면
     *         {@code Optional.empty()}</b> — 넛지도, 소비 관리 목표도, 직전 4주 지출
     *         기록도 없는 사용자다(가입 직후). 그때 COACH-03 은 제안 없이 4장 1·2번
     *         문구만 보낸다. 아무 카테고리나 찍어 주는 것보다 낫다 — 제안 카드는 그대로
     *         챌린지로 확정될 수 있다.
     */
    public Optional<ProposedGoal> propose(User user) {
        LocalDate today = KstTime.today();
        DateRange lastFourWeeks = Weeks.fullWeeksBefore(today, SAMPLE_WEEKS);

        // 월세는 제외한다 — 한 달에 한 번 큰 금액이라 ③의 "가장 큰 카테고리"를 항상
        // 가져가고, 절약 목표로 삼을 수 있는 지출도 아니다 (ERD 4장).
        Map<Long, Long> amounts =
                spendingSampler.amountsIn(user.getId(), lastFourWeeks, RentCategory.ID);

        return pickCategoryId(user, today, amounts)
                .flatMap(categoryRepository::findById)
                .map(category -> goalFor(category, amounts));
    }

    /** 5장의 ①②③ 을 순서대로. 앞의 것이 있으면 뒤는 보지 않는다. */
    private Optional<Long> pickCategoryId(User user, LocalDate today, Map<Long, Long> amounts) {
        return nudgeCategoryId(user, today)
                .or(() -> spendingFocusCategoryId(user))
                .or(() -> biggestSpendingCategoryId(amounts));
    }

    /**
     * ① 넛지에서 시작한 코칭의 카테고리.
     *
     * <p>NDG-01 과 <b>같은 쿼리</b>에 상태만 {@code COACHING_STARTED} 로 바꿔 쓴다 —
     * 이번 주 월요일이 {@code analysisStartDate} 인 카드 중 가장 최근에 갱신된 것.
     * 클래스 설명의 "추론"이 이 메서드다.
     */
    private Optional<Long> nudgeCategoryId(User user, LocalDate today) {
        return nudgeRepository
                .findTopByUserIdAndAnalysisStartDateAndStatusOrderByUpdatedAtDesc(
                        user.getId(), Weeks.mondayOf(today), NudgeStatus.COACHING_STARTED)
                .map(nudge -> nudge.getCategory().getId());
    }

    /**
     * ② 이번 달 예산의 소비 관리 목표 (ERD 3장 {@code SPENDING_FOCUS}).
     *
     * <p>{@code FIXED_COST} 는 특정 카테고리로 번역되지 않으므로 {@code empty} 를 주고 ③ 로
     * 넘긴다 — 5장이 "{@code FIXED_COST} 이거나 목표가 없으면"으로 둘을 같이 묶은 이유다.
     */
    private Optional<Long> spendingFocusCategoryId(User user) {
        return budgetRepository
                .findByUserIdAndYearMonth(user.getId(), YearMonths.current().toString())
                .map(Budget::getSpendingFocus)
                .flatMap(RuleBasedGoalProposer::categoryIdOf);
    }

    /**
     * 5장의 {@code DELIVERY} → 배달 / {@code CAFE} → 카페 매핑.
     *
     * <p>ID 를 코드에 박는 건 {@link RentCategory#ID} 와 같은 선택이다 — ERD 4장의 시드가
     * ID 1~9 를 계약으로 고정하고 있고({@code CategorySeeder}), 이름으로 조회하면 카테고리명
     * 변경에 깨진다. {@code FIXED_COST} 가 여기 없는 것이 ③ 으로 넘어가는 장치다.
     */
    private static Optional<Long> categoryIdOf(SpendingFocus focus) {
        return switch (focus) {
            case DELIVERY -> Optional.of(2L);
            case CAFE -> Optional.of(3L);
            case FIXED_COST -> Optional.empty();
        };
    }

    /**
     * ③ 직전 4주 지출이 가장 큰 카테고리 (월세는 집계에서 이미 빠져 있다).
     *
     * <p>금액이 같으면 ID 가 작은 쪽 — 임의로 흔들리지 않게 고정한다. 기록이 아예 없으면
     * {@code empty} 이고, 그게 "제안할 수 없음"의 유일한 경로다.
     */
    private static Optional<Long> biggestSpendingCategoryId(Map<Long, Long> amounts) {
        return amounts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry<Long, Long>::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey);
    }

    private ProposedGoal goalFor(Category category, Map<Long, Long> amounts) {
        int goalAmount = goalAmountFrom(amounts.getOrDefault(category.getId(), 0L));

        return new ProposedGoal(
                category.getId(),
                GoalTitle.of(category.getName(), GoalType.AMOUNT, goalAmount),
                GoalType.AMOUNT,
                goalAmount,
                null,
                PERIOD_DAYS);
    }

    /**
     * 5장: 주간 평균의 80% 를 만 원 단위로 내림, 최소 10,000원.
     *
     * <p>문서의 예시대로 계산된다: 주간 평균 60,000 → 48,000 → <b>40,000</b>.
     *
     * @param fourWeekTotal 직전 4주 합계 — 주간 평균은 이것을 4로 나눈 값이다
     */
    private static int goalAmountFrom(long fourWeekTotal) {
        long weeklyAverage = fourWeekTotal / SAMPLE_WEEKS;
        long reduced = weeklyAverage * GOAL_PERCENT / 100;

        return (int) Math.max(reduced / GOAL_UNIT * GOAL_UNIT, MIN_GOAL);
    }
}
