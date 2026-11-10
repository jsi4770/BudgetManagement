package com.back.was.domain.dev;

import com.back.was.global.support.Weeks;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 시연_데이터 3장의 거래 생성 규칙을 거래 목록으로 펼친다 — DEV-01 의 재료.
 *
 * <p><b>DB 를 모르는 순수 함수다.</b> 그래서 {@code docs/시연_거래내역.csv}(D =
 * 2026-11-04 일 때의 정답지 206건)와 직접 비교할 수 있고,
 * {@code DemoTransactionPlannerTest} 가 그 비교를 한다 — 규칙을 하나 잘못 옮기면 거기서
 * 걸린다.
 *
 * <p>날짜를 <b>D 기준 상대값</b>으로 만드는 것이 이 클래스의 전부다(시연_데이터 머리말:
 * "언제 시연해도 같은 흐름이 나와요"). 그래서 {@code today} 를 인자로 받는다 —
 * {@code KstTime.today()} 를 안에서 부르면 CSV 와 비교할 방법이 없어진다.
 *
 * <h2>요일 보정 (3-5)</h2>
 * <b>이번 주 거래와 과거 주의 배달 거래</b>는 날짜가 {@code 월요일 + min(오프셋, k − 1)}
 * 이다({@code k} = 이번 주 경과 일수). 월요일에 시연하면 화·수요일 거래가 모두 월요일로
 * 당겨지고, 그래서 <b>시연 요일과 무관하게</b> 넛지 계산이 같은 값(배달 Z = 9.32,
 * +68.33%)을 낸다. 보정이 없으면 월요일 시연에는 이번 주 배달이 한 건뿐이라 넛지가 아예
 * 뜨지 않는다.
 *
 * <p>나머지 주간 반복(3-1)에는 보정을 걸지 않는다 — 과거 주의 식비·카페·교통은 넛지 표본에
 * 들어가지 않거나(월세·수입) 요일이 바뀌어도 주 합계가 같다.
 */
@Component
public class DemoTransactionPlanner {

    /** 생성 범위의 시작 = 직전 3개월의 1일 (시연_데이터 3장). */
    private static final int RANGE_MONTHS = 3;

    /** 주간 반복은 1주 전 ~ 14주 전 (3-1). */
    private static final int RECURRING_WEEKS = 14;

    private static final long FOOD = 1L;
    private static final long DELIVERY = 2L;
    private static final long CAFE = 3L;
    private static final long TRANSPORT = 4L;
    private static final long RENT = 5L;
    private static final long ETC = 6L;
    private static final long SALARY = 7L;

    /** 3-2 배달 주문표. 인덱스가 "w주 전"이 되도록 0번은 비워 둔다. */
    private static final List<List<Order>> DELIVERY_ORDERS_BY_WEEKS_AGO = List.of(
            List.of(),                                                              // (미사용)
            List.of(new Order(0, 25_000, "치킨"), new Order(2, 30_000, "떡볶이")),    // 1주 전 55,000
            List.of(new Order(0, 29_000, "피자"), new Order(2, 33_000, "마라탕")),    // 2주 전 62,000
            List.of(new Order(0, 32_000, "족발"), new Order(2, 26_000, "햄버거")),    // 3주 전 58,000
            List.of(new Order(0, 31_000, "치킨"), new Order(2, 34_000, "찜닭")),      // 4주 전 65,000
            List.of(new Order(0, 27_000, "떡볶이"), new Order(2, 34_000, "피자")),    // 5주 전 61,000
            List.of(new Order(0, 17_000, "김밥·라면"), new Order(2, 18_000, "햄버거")),// 6주 전 35,000 (C4)
            List.of(new Order(0, 30_000, "마라탕"), new Order(2, 34_000, "족발")),    // 7주 전 64,000
            List.of(new Order(0, 25_000, "치킨"), new Order(2, 32_000, "피자")),      // 8주 전 57,000
            List.of(new Order(0, 33_000, "찜닭"), new Order(2, 33_000, "떡볶이")),    // 9주 전 66,000
            List.of(new Order(0, 18_000, "햄버거"), new Order(1, 22_000, "치킨"),
                    new Order(2, 19_000, "떡볶이")),                                 // 10주 전 59,000 3회 (C2)
            List.of(new Order(0, 28_000, "피자"), new Order(2, 32_000, "마라탕")),    // 11주 전 60,000
            List.of(new Order(0, 31_000, "족발"), new Order(2, 32_000, "치킨")),      // 12주 전 63,000
            List.of(new Order(0, 27_000, "떡볶이"), new Order(2, 31_000, "피자"))     // 13주 전 58,000
    );

    /** 3-4 이번 주 배달 — 시나리오가 갈리는 유일한 지점. */
    private static final List<Order> THIS_WEEK_DELIVERY_SPIKE = List.of(
            new Order(0, 26_500, "마라탕"),
            new Order(1, 32_000, "피자"),
            new Order(1, 18_500, "떡볶이"),
            new Order(2, 24_000, "치킨")
    );

    private static final List<Order> THIS_WEEK_DELIVERY_NORMAL = List.of(
            new Order(0, 27_000, "마라탕"),
            new Order(1, 31_000, "피자")
    );

    /** 3-4 이번 주의 나머지 — 시나리오와 무관하게 같다. */
    private static final List<Planned> THIS_WEEK_COMMON = List.of(
            new Planned(0, FOOD, 4_500, "편의점 도시락"),
            new Planned(0, TRANSPORT, 1_650, "지하철"),
            new Planned(1, CAFE, 4_500, "아메리카노"),
            new Planned(2, FOOD, 4_500, "김밥")
    );

    /** 오프셋·금액·메모만 가진 배달 주문. */
    private record Order(int offset, int amount, String memo) {
    }

    /** 오프셋 기준의 일반 거래. */
    private record Planned(int offset, long categoryId, int amount, String memo) {
    }

    /**
     * @param today 시연일 D. 모든 날짜가 이 값 기준의 상대 날짜로 만들어진다
     * @return 생성 범위(직전 3개월의 1일 ~ D) 안의 거래 전부. 범위 밖은 버린다 —
     *         14주 전은 대개 3개월 전 1일보다 앞서서 잘린다
     */
    public List<PlannedTransaction> plan(LocalDate today, DemoScenario scenario) {
        LocalDate monday = Weeks.mondayOf(today);
        LocalDate rangeStart = today.minusMonths(RANGE_MONTHS).withDayOfMonth(1);

        List<PlannedTransaction> planned = new ArrayList<>();
        addRecurringWeeks(planned, monday);
        addDeliveryWeeks(planned, monday, today);
        addMonthlyFirstDays(planned, today);
        addThisWeek(planned, monday, today, scenario);

        return planned.stream()
                .filter(transaction -> !transaction.date().isBefore(rangeStart)
                        && !transaction.date().isAfter(today))
                .toList();
    }

    /** 3-1 매주 반복 (1주 전 ~ 14주 전). 요일 보정 없이 실제 요일에 들어간다. */
    private static void addRecurringWeeks(List<PlannedTransaction> planned, LocalDate monday) {
        for (int weeksAgo = 1; weeksAgo <= RECURRING_WEEKS; weeksAgo++) {
            LocalDate weekMonday = monday.minusWeeks(weeksAgo);

            add(planned, weekMonday, 0, FOOD, 9_000, "점심");
            add(planned, weekMonday, 2, FOOD, 8_500, "점심");
            add(planned, weekMonday, 4, FOOD, 9_500, "점심");
            add(planned, weekMonday, 5, FOOD, 32_000, "장보기");

            add(planned, weekMonday, 1, CAFE, 4_500, "아메리카노");
            add(planned, weekMonday, 3, CAFE, 4_500, "아메리카노");
            // 12주 전 토요일은 없다 — 챌린지 C1(카페 30,000원 이내) 기간이라 실적 9,000원을
            // 맞추기 위해 디저트 카페를 빼 둔 것이다 (시연_데이터 3-1 예외)
            if (weeksAgo != 12) {
                add(planned, weekMonday, 5, CAFE, 6_000, "디저트 카페");
            }

            for (int offset = 0; offset <= 4; offset++) {
                // 8주 전 금요일은 없다 — 챌린지 C3(교통 20,000원 이내)의 실적 12,400원 4회
                if (weeksAgo == 8 && offset == 4) {
                    continue;
                }
                add(planned, weekMonday, offset, TRANSPORT, 3_100, "출퇴근 지하철");
            }

            if (weeksAgo % 2 == 0) {
                add(planned, weekMonday, 6, ETC, 15_000, "생활용품");
            }
        }
    }

    /**
     * 3-2 배달. <b>요일 보정이 걸린다</b>(3-5) — 과거 주의 배달이 넛지 표본이고, 보정이
     * 없으면 시연 요일에 따라 표본의 건수가 달라져 Z 가 흔들린다.
     */
    private static void addDeliveryWeeks(List<PlannedTransaction> planned, LocalDate monday,
                                         LocalDate today) {
        for (int weeksAgo = 1; weeksAgo < DELIVERY_ORDERS_BY_WEEKS_AGO.size(); weeksAgo++) {
            LocalDate weekMonday = monday.minusWeeks(weeksAgo);

            for (Order order : DELIVERY_ORDERS_BY_WEEKS_AGO.get(weeksAgo)) {
                planned.add(new PlannedTransaction(
                        adjusted(weekMonday, order.offset(), today), DELIVERY,
                        order.amount(), order.memo()));
            }
        }
    }

    /** 3-3 매월 1일 — 직전 3개월 + 이번 달의 급여와 월세. */
    private static void addMonthlyFirstDays(List<PlannedTransaction> planned, LocalDate today) {
        for (int monthsAgo = 0; monthsAgo <= RANGE_MONTHS; monthsAgo++) {
            LocalDate firstDay = today.minusMonths(monthsAgo).withDayOfMonth(1);

            planned.add(new PlannedTransaction(firstDay, SALARY, 2_400_000, "월급"));
            planned.add(new PlannedTransaction(firstDay, RENT, 550_000, "월세"));
        }
    }

    /** 3-4 이번 주 (M0 ~ D). 전부 요일 보정 대상이다. */
    private static void addThisWeek(List<PlannedTransaction> planned, LocalDate monday,
                                    LocalDate today, DemoScenario scenario) {
        List<Order> delivery = scenario == DemoScenario.DELIVERY_SPIKE
                ? THIS_WEEK_DELIVERY_SPIKE
                : THIS_WEEK_DELIVERY_NORMAL;

        for (Order order : delivery) {
            planned.add(new PlannedTransaction(
                    adjusted(monday, order.offset(), today), DELIVERY,
                    order.amount(), order.memo()));
        }
        for (Planned common : THIS_WEEK_COMMON) {
            planned.add(new PlannedTransaction(
                    adjusted(monday, common.offset(), today), common.categoryId(),
                    common.amount(), common.memo()));
        }
    }

    /**
     * 시연_데이터 3-5 요일 보정: {@code 월요일 + min(오프셋, k − 1)}.
     *
     * <p>{@code k − 1} 은 "이번 주에서 쓸 수 있는 가장 큰 오프셋"이다 — 수요일 시연이면
     * {@code k = 3} 이라 오프셋 0·1·2 는 그대로, 3 이상은 수요일로 당겨진다.
     */
    private static LocalDate adjusted(LocalDate weekMonday, int offset, LocalDate today) {
        return weekMonday.plusDays(Math.min(offset, Weeks.elapsedDays(today) - 1));
    }

    private static void add(List<PlannedTransaction> planned, LocalDate weekMonday, int offset,
                            long categoryId, int amount, String memo) {
        planned.add(new PlannedTransaction(weekMonday.plusDays(offset), categoryId, amount, memo));
    }
}
