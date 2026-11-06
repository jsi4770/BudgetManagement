package com.back.was.domain.transaction.dto;

import com.back.was.domain.transaction.DailyAmountSum;

import java.time.LocalDate;
import java.util.List;

/**
 * TX-03 응답.
 *
 * <p>{@code days} 에는 <b>거래가 있는 날짜만</b> 들어간다(명세 TX-03). 빈 날짜를 0 으로
 * 채우지 않는 쪽이 달력 UI 에 유리하다 — 프론트는 날짜 칸을 이미 그려 두고 값이 있는
 * 날에만 금액을 얹는다.
 */
public record CalendarResponse(
        String yearMonth,
        List<Day> days
) {
    /**
     * 하루치 합계. 수입·지출을 각각 더하므로 한 날짜에 두 값이 함께 올 수 있다.
     *
     * <p>금액은 명세 0장 2)대로 int(원)다.
     */
    public record Day(
            LocalDate date,
            int totalIncome,
            int totalExpense
    ) {
        static Day from(DailyAmountSum sum) {
            return new Day(sum.date(), toAmount(sum.income()), toAmount(sum.expense()));
        }

        /**
         * JPQL {@code sum()} 의 {@code Long} 을 명세의 int 로 좁힌다.
         *
         * <p>조용히 잘리지 않게 {@link Math#toIntExact} 를 쓴다 — 하루 합계가 21억 원을
         * 넘는 건 명세의 금액 타입(int) 자체를 벗어난 입력이므로, 틀린 숫자를 응답하는
         * 것보다 500 으로 드러나는 편이 낫다.
         */
        private static int toAmount(long sum) {
            return Math.toIntExact(sum);
        }
    }

    public static CalendarResponse of(String yearMonth, List<DailyAmountSum> sums) {
        return new CalendarResponse(yearMonth, sums.stream().map(Day::from).toList());
    }
}
