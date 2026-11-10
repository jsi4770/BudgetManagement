package com.back.was.domain.dev;

import com.back.was.global.support.Weeks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 시연 거래 생성 규칙 (시연_데이터 3장).
 *
 * <p><b>정답지가 문서에 있다</b>: {@code docs/시연_거래내역.csv} 는 D = 2026-11-04 일 때의
 * 206건이다. 그래서 가장 중요한 테스트는 생성 결과를 그 CSV 와 그대로 비교하는 것이고,
 * 규칙 한 줄을 잘못 옮기면 거기서 걸린다.
 *
 * <p>CSV 는 {@code .gitignore} 대상인 {@code docs/} 안에 있어서 체크아웃에 없을 수 있다 —
 * 그때는 {@code assumeTrue} 로 건너뛰고, 나머지 테스트가 규칙을 직접 검증한다.
 */
class DemoTransactionPlannerTest {

    /** 시연_데이터의 기준일 D (수요일). CSV 가 이 날짜 기준이다. */
    private static final LocalDate D = LocalDate.of(2026, 11, 4);

    private static final Path CSV = Path.of("docs/시연_거래내역.csv");

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;
    private static final long RENT = 5L;
    private static final long SALARY = 7L;

    private final DemoTransactionPlanner planner = new DemoTransactionPlanner();

    /** CSV 한 줄을 계획 한 건으로. 열 순서: date, category_id, category, type, amount, memo. */
    private static PlannedTransaction parse(String line) {
        String[] columns = line.split(",", -1);
        return new PlannedTransaction(
                LocalDate.parse(columns[0]),
                Long.parseLong(columns[1]),
                Integer.parseInt(columns[4]),
                columns[5]);
    }

    private static List<PlannedTransaction> sorted(List<PlannedTransaction> transactions) {
        return transactions.stream()
                .sorted(Comparator.comparing(PlannedTransaction::date)
                        .thenComparingLong(PlannedTransaction::categoryId)
                        .thenComparingInt(PlannedTransaction::amount)
                        .thenComparing(PlannedTransaction::memo))
                .toList();
    }

    private List<PlannedTransaction> plan(LocalDate today, DemoScenario scenario) {
        return planner.plan(today, scenario);
    }

    private static int sumOf(List<PlannedTransaction> transactions, long categoryId,
                             LocalDate from, LocalDate to) {
        return transactions.stream()
                .filter(transaction -> transaction.categoryId() == categoryId
                        && !transaction.date().isBefore(from)
                        && !transaction.date().isAfter(to))
                .mapToInt(PlannedTransaction::amount)
                .sum();
    }

    // --- 문서의 정답지와 비교 -----------------------------------------------

    @Test
    @DisplayName("D = 2026-11-04 의 DELIVERY_SPIKE 는 docs/시연_거래내역.csv 와 같다 (206건)")
    void matchesTheDocumentedCsv() throws IOException {
        assumeTrue(Files.exists(CSV), "docs/시연_거래내역.csv 가 없으면 건너뛴다");

        List<String> lines = Files.readAllLines(CSV);
        List<PlannedTransaction> expected = lines.stream()
                .skip(1)                                  // 헤더
                .filter(line -> !line.isBlank())
                .map(DemoTransactionPlannerTest::parse)
                .toList();

        // 순서는 비교하지 않는다 — 생성 순서를 CSV 의 줄 순서에 묶으면 규칙을 고칠 때마다
        // 의미 없는 실패가 난다. 내용과 건수가 같은지만 본다.
        assertThat(sorted(plan(D, DemoScenario.DELIVERY_SPIKE)))
                .containsExactlyElementsOf(sorted(expected));
        assertThat(expected).hasSize(206);
    }

    // --- 예상 결과 (시연_데이터 3-4 · 6-1) ----------------------------------

    @Test
    @DisplayName("이번 주 배달: DELIVERY_SPIKE 101,000원 4회 / NORMAL 58,000원 2회")
    void thisWeekDeliveryMatchesTheDocument() {
        LocalDate monday = Weeks.mondayOf(D);

        List<PlannedTransaction> spike = plan(D, DemoScenario.DELIVERY_SPIKE);
        List<PlannedTransaction> normal = plan(D, DemoScenario.NORMAL);

        assertThat(sumOf(spike, DELIVERY, monday, D)).isEqualTo(101_000);
        assertThat(sumOf(normal, DELIVERY, monday, D)).isEqualTo(58_000);
        assertThat(spike.stream().filter(t -> t.categoryId() == DELIVERY
                && !t.date().isBefore(monday))).hasSize(4);
        assertThat(normal.stream().filter(t -> t.categoryId() == DELIVERY
                && !t.date().isBefore(monday))).hasSize(2);
    }

    @Test
    @DisplayName("이번 주 합계(월세 제외) 116,150원 — 명세 ANL-02 시연값")
    void thisWeekTotalExcludingRent() {
        LocalDate monday = Weeks.mondayOf(D);

        int total = plan(D, DemoScenario.DELIVERY_SPIKE).stream()
                .filter(t -> !t.date().isBefore(monday) && t.categoryId() != RENT
                        && t.categoryId() != SALARY)
                .mapToInt(PlannedTransaction::amount)
                .sum();

        assertThat(total).isEqualTo(116_150);
    }

    @Test
    @DisplayName("넛지 표본: 직전 4주의 월~수 배달이 55/62/58/65천 (시연 6-1 → 평균 60,000)")
    void nudgeSamplesMatchTheDocument() {
        LocalDate monday = Weeks.mondayOf(D);
        List<PlannedTransaction> planned = plan(D, DemoScenario.DELIVERY_SPIKE);
        int[] expected = {55_000, 62_000, 58_000, 65_000};

        for (int weeksAgo = 1; weeksAgo <= 4; weeksAgo++) {
            LocalDate weekMonday = monday.minusWeeks(weeksAgo);

            assertThat(sumOf(planned, DELIVERY, weekMonday, weekMonday.plusDays(2)))
                    .as("%d주 전 월~수 배달", weeksAgo)
                    .isEqualTo(expected[weeksAgo - 1]);
        }
    }

    @Test
    @DisplayName("과거 챌린지 기간의 실적이 문서와 맞는다 (C1 카페 9,000 · C2 배달 3회 · C4 배달 35,000)")
    void pastChallengePeriodsMatchTheDocument() {
        LocalDate monday = Weeks.mondayOf(D);
        List<PlannedTransaction> planned = plan(D, DemoScenario.DELIVERY_SPIKE);

        // C1: 12주 전 월~일, 카페 — 토요일 디저트를 뺀 화·목 아메리카노 2건
        LocalDate c1 = monday.minusWeeks(12);
        assertThat(sumOf(planned, CAFE, c1, c1.plusDays(6))).isEqualTo(9_000);

        // C2: 10주 전, 배달 3회 59,000원
        LocalDate c2 = monday.minusWeeks(10);
        assertThat(sumOf(planned, DELIVERY, c2, c2.plusDays(6))).isEqualTo(59_000);

        // C3: 8주 전, 교통 — 금요일을 뺀 4건 12,400원
        LocalDate c3 = monday.minusWeeks(8);
        assertThat(sumOf(planned, TRANSPORT, c3, c3.plusDays(6))).isEqualTo(12_400);

        // C4: 6주 전, 배달 35,000원 / 그 직전 주(7주 전)가 BEFORE 64,000원
        LocalDate c4 = monday.minusWeeks(6);
        assertThat(sumOf(planned, DELIVERY, c4, c4.plusDays(6))).isEqualTo(35_000);
        assertThat(sumOf(planned, DELIVERY, c4.minusWeeks(1), c4.minusDays(1))).isEqualTo(64_000);
    }

    @Test
    @DisplayName("매월 1일에 급여 2,400,000 과 월세 550,000 — 직전 3개월 + 이번 달 = 4번")
    void monthlyFirstDays() {
        List<PlannedTransaction> planned = plan(D, DemoScenario.DELIVERY_SPIKE);

        assertThat(planned.stream().filter(t -> t.categoryId() == SALARY))
                .hasSize(4)
                .allSatisfy(t -> {
                    assertThat(t.amount()).isEqualTo(2_400_000);
                    assertThat(t.date().getDayOfMonth()).isEqualTo(1);
                });
        assertThat(planned.stream().filter(t -> t.categoryId() == RENT)).hasSize(4);
    }

    @Test
    @DisplayName("생성 범위를 벗어난 날짜는 넣지 않는다 (직전 3개월의 1일 ~ D)")
    void staysWithinTheRange() {
        LocalDate rangeStart = D.minusMonths(3).withDayOfMonth(1);

        assertThat(plan(D, DemoScenario.DELIVERY_SPIKE)).allSatisfy(transaction ->
                assertThat(transaction.date()).isBetween(rangeStart, D));
    }

    // --- 요일 보정 (시연_데이터 3-5) ----------------------------------------

    @Nested
    @DisplayName("요일 보정")
    class WeekdayAdjustment {

        @Test
        @DisplayName("어느 요일에 시연해도 이번 주 배달은 101,000원 4회다")
        void thisWeekDeliveryIsStableAcrossWeekdays() {
            // 2026-11-02(월) ~ 11-08(일) 일곱 요일 전부
            for (int dayOfMonth = 2; dayOfMonth <= 8; dayOfMonth++) {
                LocalDate today = LocalDate.of(2026, 11, dayOfMonth);
                LocalDate monday = Weeks.mondayOf(today);
                List<PlannedTransaction> planned = plan(today, DemoScenario.DELIVERY_SPIKE);

                assertThat(sumOf(planned, DELIVERY, monday, today))
                        .as("%s 의 이번 주 배달 합계", today)
                        .isEqualTo(101_000);
            }
        }

        @Test
        @DisplayName("어느 요일에 시연해도 직전 4주 표본이 55/62/58/65천이다 (Z = 9.32 고정)")
        void nudgeSamplesAreStableAcrossWeekdays() {
            int[] expected = {55_000, 62_000, 58_000, 65_000};

            for (int dayOfMonth = 2; dayOfMonth <= 8; dayOfMonth++) {
                LocalDate today = LocalDate.of(2026, 11, dayOfMonth);
                LocalDate monday = Weeks.mondayOf(today);
                List<PlannedTransaction> planned = plan(today, DemoScenario.DELIVERY_SPIKE);

                for (int weeksAgo = 1; weeksAgo <= 4; weeksAgo++) {
                    LocalDate weekMonday = monday.minusWeeks(weeksAgo);
                    // 표본 구간은 월요일 ~ k번째 날이다 (넛지·ANL-03 의 정의)
                    assertThat(sumOf(planned, DELIVERY, weekMonday, weekMonday.plusDays(
                            Weeks.elapsedDays(today) - 1)))
                            .as("%s 기준 %d주 전 표본", today, weeksAgo)
                            .isEqualTo(expected[weeksAgo - 1]);
                }
            }
        }

        @Test
        @DisplayName("월요일 시연이면 이번 주 거래가 모두 월요일에 모인다")
        void mondayDemoCollapsesEverythingToMonday() {
            LocalDate monday = LocalDate.of(2026, 11, 2);

            assertThat(plan(monday, DemoScenario.DELIVERY_SPIKE).stream()
                    .filter(transaction -> !transaction.date().isBefore(monday)))
                    .isNotEmpty()
                    .allSatisfy(transaction -> assertThat(transaction.date()).isEqualTo(monday));
        }

        @Test
        @DisplayName("보정은 이번 주와 과거 배달에만 — 식비·교통은 실제 요일에 남는다")
        void recurringCategoriesKeepTheirWeekday() {
            LocalDate monday = LocalDate.of(2026, 11, 2);
            List<PlannedTransaction> planned = plan(monday, DemoScenario.DELIVERY_SPIKE);

            // 1주 전 토요일 장보기 32,000원은 그대로 토요일이다
            LocalDate lastSaturday = monday.minusWeeks(1).plusDays(5);
            assertThat(planned)
                    .anySatisfy(transaction -> {
                        assertThat(transaction.date()).isEqualTo(lastSaturday);
                        assertThat(transaction.categoryId()).isEqualTo(FOOD);
                        assertThat(transaction.amount()).isEqualTo(32_000);
                    });
        }
    }
}
