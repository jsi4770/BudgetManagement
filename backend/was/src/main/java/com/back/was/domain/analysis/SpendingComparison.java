package com.back.was.domain.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * "이번 기간 금액 vs 평소 같은 기간 표본" 비교 결과. 서버_내부_처리 3장의 Z-score 계산을
 * <b>순수 함수</b>로 떼어낸 것이다 — DB·사용자·카테고리를 모르고 숫자만 받는다.
 *
 * <p><b>넛지 판정과 ANL-03 이 반드시 이 계산을 공유해야 한다</b>(서버_내부_처리 3장:
 * "ANL-03(period=WEEK)과 같은 계산 함수 사용 → 넛지 평균과 분석 화면 평균이 항상 일치").
 * 한쪽에 따로 구현하면 화면의 "평소 평균"과 넛지 카드의 평균이 조용히 어긋난다.
 *
 * <p>주간 전용이 아니다. ANL-03 {@code MONTH} 는 표본이 "직전 3개월의 1일 ~ 같은 날짜"로
 * 바뀔 뿐 평균·증가율 계산이 같다. 그래서 이름에 week 가 없고, 구간을 만드는 일은
 * {@link com.back.was.global.support.Weeks} · {@link com.back.was.global.support.Months} 가
 * 맡는다. 다만 {@link #isAnomaly()} 는 주간 규칙이다 — 월간 탭은 이상 표시를 하지 않으므로
 * (명세 ANL-03) 호출하지 않는다.
 *
 * <p>여기서 <b>판정하지 않는 것</b>: 데이터 충분성(가입일이 표본 첫 구간 시작보다 뒤면
 * 판정 생략 / ANL-03 {@code dataSufficient} = false)은 가입일을 알아야 하므로 서비스 몫이다.
 * 월세 제외도 표본을 모으는 쪽에서 끝낸다({@link com.back.was.domain.category.RentCategory}).
 *
 * @param amount            이번 기간 금액 (NUDGE.SPENDING_AMOUNT)
 * @param samples           표본. <b>기록 없는 구간도 0원으로 채워진 상태</b>여야 한다(ERD 6장)
 * @param average           표본 평균, 원 단위로 반올림 (NUDGE.AVG_AMOUNT 가 INT)
 * @param standardDeviation 표본표준편차 (n − 1 로 나눔)
 * @param zScore            {@code (금액 − 평균) ÷ 표준편차}. <b>표준편차 0 이면 {@code null}</b>
 * @param growthRate        {@code (금액 − 평균) ÷ 평균 × 100}. <b>평균 0 이면 {@code null}</b>
 */
public record SpendingComparison(
        int amount,
        List<Integer> samples,
        int average,
        BigDecimal standardDeviation,
        BigDecimal zScore,
        BigDecimal growthRate
) {
    /** 이상 판정 임계값 (서버_내부_처리 3장). 넛지 생성 조건과 ANL-03 {@code isAnomaly} 가 공유한다. */
    public static final BigDecimal ANOMALY_Z_SCORE = new BigDecimal("2");

    /** 이상 판정의 금액 하한 — 평균보다 이만큼 이상 더 썼을 때만 (서버_내부_처리 3장). */
    public static final int ANOMALY_AMOUNT_DIFF = 30_000;

    /** CLAUDE.md: 비율은 {@code BigDecimal} scale 2. NUDGE 의 {@code DECIMAL(10,2)} 와도 맞는다. */
    private static final int RATE_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param samples 표본. 표본표준편차는 {@code n − 1} 로 나누므로 2개 이상이어야 한다
     *                (넛지·ANL-03 {@code WEEK} 는 4개, {@code MONTH} 는 3개)
     * @throws IllegalArgumentException 표본이 2개 미만일 때
     */
    public static SpendingComparison of(int amount, List<Integer> samples) {
        if (samples == null || samples.size() < 2) {
            throw new IllegalArgumentException(
                    "표본은 2개 이상이어야 해요 (표본표준편차는 n − 1 로 나눕니다).");
        }
        List<Integer> copied = List.copyOf(samples);

        int average = average(copied);
        BigDecimal standardDeviation = standardDeviation(copied, average);
        BigDecimal difference = BigDecimal.valueOf((long) amount - average);

        // 표준편차 0 = 표본이 전부 같은 값. 나누면 무한이 되므로 판정 자체를 생략한다
        // (ERD 6장 "판정 생략 ② 표준편차가 0"). 그 경우 Z 는 응답에도 쓰이지 않는다.
        BigDecimal zScore = standardDeviation.signum() == 0 ? null
                : difference.divide(standardDeviation, RATE_SCALE, RoundingMode.HALF_UP);

        // 표준편차가 0보다 크면 평균도 0보다 크므로(ERD 6장 주석) 실제로 null 이 되는 건
        // "표본이 전부 0원"일 때뿐이고, 그때는 Z 도 null 이다.
        BigDecimal growthRate = average == 0 ? null
                : difference.multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(average), RATE_SCALE, RoundingMode.HALF_UP);

        return new SpendingComparison(amount, copied, average, standardDeviation, zScore, growthRate);
    }

    /** 평균보다 더 쓴 금액. 음수면 평소보다 덜 썼다는 뜻이다. */
    public int difference() {
        return amount - average;
    }

    /**
     * 넛지 생성 조건 = ANL-03 {@code WEEK} 의 {@code isAnomaly}:
     * <b>Z ≥ 2 AND (금액 − 평균) ≥ 30,000원</b> (서버_내부_처리 3장).
     *
     * <p>Z 조건만 쓰면 평소 1,000원짜리가 3,000원이 된 경우에도 카드가 뜬다 — 금액 하한이
     * 그걸 막는다. 표준편차 0(= 판정 생략)이면 {@code zScore} 가 {@code null} 이라 여기서도
     * 자동으로 {@code false} 다.
     */
    public boolean isAnomaly() {
        return zScore != null
                && zScore.compareTo(ANOMALY_Z_SCORE) >= 0
                && difference() >= ANOMALY_AMOUNT_DIFF;
    }

    /**
     * 표본 평균을 원 단위로 반올림한다 — NUDGE.AVG_AMOUNT 가 INT 이고, 화면에도 원 단위로
     * 나간다(ANL-03 {@code averageAmount}).
     *
     * <p>Z·증가율도 이 <b>반올림된 평균</b>으로 계산한다. 정확한 실수 평균을 따로 쓰면
     * 저장·표시되는 평균과 증가율이 미세하게 어긋나, "평균 8,267원인데 증가율은 8,266.67원
     * 기준" 같은 설명 불가능한 상태가 된다.
     */
    private static int average(List<Integer> samples) {
        long sum = samples.stream().mapToLong(Integer::longValue).sum();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(samples.size()), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /**
     * 표본표준편차 — 편차 제곱합을 {@code n − 1} 로 나눈 뒤 제곱근 (ERD 6장).
     *
     * <p>모표준편차({@code n})가 아닌 이유는 직전 4주가 "평소"의 표본일 뿐 모집단이
     * 아니기 때문이고, 명세가 그렇게 못박았다.
     */
    private static BigDecimal standardDeviation(List<Integer> samples, int average) {
        BigDecimal squaredSum = samples.stream()
                .map(sample -> BigDecimal.valueOf((long) sample - average).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return squaredSum
                .divide(BigDecimal.valueOf(samples.size() - 1L), MathContext.DECIMAL64)
                .sqrt(MathContext.DECIMAL64)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }
}
