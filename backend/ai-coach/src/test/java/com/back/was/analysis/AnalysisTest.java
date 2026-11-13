package com.back.was.analysis;

import com.back.was.category.CategoryProviderImpl;
import com.back.was.common.TimeUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 계획표의 분석 테스트 항목 */
class AnalysisTest {

    private final LocalDate today = TimeUtil.today();

    private CategoryComparisonService service(FakeDataPort port) {
        return new CategoryComparisonService(port, new CategoryProviderImpl());
    }

    @Test
    void 시드_데이터에서_배달만_이상치() {
        CategoryComparison week = service(FakeDataPort.seed(today, true)).compareWeek(1L, today);

        assertThat(week.dataSufficient()).isTrue();
        assertThat(week.items()).filteredOn(CategoryComparison.Item::isAnomaly)
                .extracting(i -> i.category().name())
                .containsExactly("배달");
    }

    @Test
    void 가입_2주_계정은_이상치_없이_dataSufficient_false() {
        FakeDataPort port = FakeDataPort.seed(today, true);
        port.signup = today.minusWeeks(2);
        CategoryComparison week = service(port).compareWeek(1L, today);

        assertThat(week.dataSufficient()).isFalse();
        assertThat(week.items()).noneMatch(CategoryComparison.Item::isAnomaly);
        assertThat(week.items()).allMatch(i -> i.averageAmount() == null && i.growthRate() == null);
    }

    @Test
    void 월세는_주간_집계에서_빠짐() {
        FakeDataPort port = FakeDataPort.seed(today, false).add(5, 550_000, today, "월세");
        CategoryComparison week = service(port).compareWeek(1L, today);

        assertThat(week.items()).noneMatch(i -> i.category().name().equals("월세"));

        List<WeeklyTrendService.WeekTotal> trend =
                new WeeklyTrendService(port, new CategoryProviderImpl()).weekly(1L, 4, today);
        assertThat(trend.get(3).totalExpense()).isLessThan(550_000);
    }

    @Test
    void MONTH_는_항상_이상치_false_월세는_평균_null() {
        FakeDataPort port = FakeDataPort.seed(today, true).add(5, 550_000, today.withDayOfMonth(1), "월세");
        CategoryComparison month = service(port).compareMonth(1L, today);

        assertThat(month.items()).noneMatch(CategoryComparison.Item::isAnomaly);
        assertThat(month.items()).filteredOn(i -> i.category().name().equals("월세"))
                .allMatch(i -> i.averageAmount() == null && i.growthRate() == null);
    }

    @Test
    void 판정_경계값() {
        // Z 는 크지만 차이가 3만원 미만 → false
        assertThat(AnomalyJudge.judge(14_000, List.of(10_000L, 10_000L, 10_000L, 11_000L)).anomaly()).isFalse();
        // Z ≥ 2 AND +30,000원 이상 → true
        assertThat(AnomalyJudge.judge(80_000, List.of(20_000L, 25_000L, 22_000L, 18_000L)).anomaly()).isTrue();
    }
}
