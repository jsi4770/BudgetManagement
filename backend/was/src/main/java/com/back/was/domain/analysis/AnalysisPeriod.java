package com.back.was.domain.analysis;

import com.back.was.global.support.DateRange;
import com.back.was.global.support.Months;
import com.back.was.global.support.Weeks;

import java.time.LocalDate;

/**
 * ANL-03 의 비교 단위 (명세 ANL-03 {@code period}).
 *
 * <p>두 값의 차이가 다섯 군데에 퍼져 있어서 — 분석 구간, 표본 구간, 표본 개수, 월세 처리,
 * 이상 표시 — 서비스에 {@code if (period == WEEK)} 를 다섯 번 쓰는 대신 여기 모았다. 하나를
 * 고칠 때 나머지를 빠뜨리는 게 이 API 에서 가장 하기 쉬운 실수다.
 *
 * <table border="1">
 *   <caption>명세 ANL-03 표</caption>
 *   <tr><th></th><th>{@code WEEK}</th><th>{@code MONTH}</th></tr>
 *   <tr><td>분석 기간</td><td>해당 주 월요일 ~ baseDate</td><td>1일 ~ baseDate</td></tr>
 *   <tr><td>표본</td><td>직전 4주의 "월요일 ~ 같은 요일"</td><td>직전 3개월의 "1일 ~ 같은 날짜"</td></tr>
 *   <tr><td>월세</td><td>합계·항목에서 제외</td><td>항목 포함, 비교(평균·증가율)만 제외</td></tr>
 *   <tr><td>{@code isAnomaly}</td><td>넛지 생성 조건과 동일</td><td>항상 {@code false}</td></tr>
 * </table>
 */
public enum AnalysisPeriod {

    /** 직전 4주 비교. 넛지 판정과 같은 기준이어야 한다 (서버_내부_처리 3장). */
    WEEK(4, true, true),

    /** 직전 3개월 비교. 월간 탭은 이상 표시를 하지 않는다 (명세 ANL-03). */
    MONTH(3, false, false);

    private final int sampleCount;
    private final boolean rentExcluded;
    private final boolean anomalyDetected;

    AnalysisPeriod(int sampleCount, boolean rentExcluded, boolean anomalyDetected) {
        this.sampleCount = sampleCount;
        this.rentExcluded = rentExcluded;
        this.anomalyDetected = anomalyDetected;
    }

    /** 표본 개수 — {@code WEEK} 4주, {@code MONTH} 3개월. */
    public int sampleCount() {
        return sampleCount;
    }

    /**
     * 월세를 <b>항목과 합계에서</b> 뺄지 (ERD 4장).
     *
     * <p>{@code MONTH} 가 {@code false} 인 건 월세를 비교한다는 뜻이 아니다 — 항목과
     * {@code totalAmount} 에는 넣고(홈 총지출과 같은 값이어야 함), 평균·증가율만 빼기 때문에
     * 그 제외는 항목을 만들 때 따로 판단한다.
     */
    public boolean isRentExcluded() {
        return rentExcluded;
    }

    /** {@code isAnomaly} 를 판정할지. {@code MONTH} 는 화면에 이상 표시가 없어 항상 {@code false}. */
    public boolean isAnomalyDetected() {
        return anomalyDetected;
    }

    /** 분석 기간 — {@code WEEK} 는 월요일 ~ baseDate, {@code MONTH} 는 1일 ~ baseDate. */
    public DateRange analysisRange(LocalDate baseDate) {
        return switch (this) {
            case WEEK -> Weeks.elapsedWeekOf(baseDate);
            case MONTH -> Months.elapsedMonthOf(baseDate);
        };
    }

    /**
     * 표본 구간 하나. 분석 기간과 <b>같은 경과 일수</b>로 잘린다 — 그래서 수요일에 조회해도
     * 수요일까지끼리 비교된다 (ERD 6장).
     *
     * @param periodsAgo 1 이면 지난 주/지난 달, {@link #sampleCount()} 가 가장 오래된 구간
     */
    public DateRange sampleRange(LocalDate baseDate, int periodsAgo) {
        return switch (this) {
            case WEEK -> Weeks.elapsedWeeksAgo(baseDate, periodsAgo);
            case MONTH -> Months.elapsedMonthsAgo(baseDate, periodsAgo);
        };
    }

    /**
     * 가장 오래된 표본 구간의 시작일 = {@code dataSufficient} 의 기준일.
     *
     * <p>명세 ANL-03 의 두 문장("{@code WEEK}: 가입 4주 미만", "{@code MONTH}: 가입일이 직전
     * 3개월 첫날 이후")이 결국 같은 규칙이라서 한 메서드로 둔다 — <b>가입일이 이 날짜보다
     * 뒤면 비교할 기록이 없다</b>. 넛지의 "판정 생략 ① 가입일이 표본 첫 주 월요일 이후"와도
     * 같은 식이다.
     */
    public LocalDate oldestSampleStart(LocalDate baseDate) {
        return sampleRange(baseDate, sampleCount).start();
    }
}
