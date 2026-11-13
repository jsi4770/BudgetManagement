package com.back.was.domain.analysis;

import java.util.List;
import java.util.Map;

/**
 * "이번 기간"과 "표본 구간들"의 카테고리별 지출 집계를 한 묶음으로 들고 있는 값.
 *
 * <p>ANL-03 과 넛지 판정이 <b>같은 평균을 쓰게 만드는 지점</b>이다(서버_내부_처리 3장).
 * 둘이 각자 표본을 조립하면 한쪽만 "기록 없는 구간 0원 채우기"나 월세 제외를 빠뜨려도
 * 아무 신호가 없다 — 그 조립을 {@link #compare(Long)} 한 곳에 모았다.
 *
 * @param amounts 분석 기간의 카테고리별 합계. 지출이 없는 카테고리는 <b>키가 없다</b>
 * @param samples 표본 구간별 같은 모양의 맵. 오래된 구간부터. <b>비어 있으면 비교 불가</b>
 *                (ANL-03 의 {@code dataSufficient: false})
 */
public record SpendingSamples(
        Map<Long, Long> amounts,
        List<Map<Long, Long>> samples
) {
    /** 지출이 없으면 0원. 그 카테고리를 목록에서 빼지 않는 건 호출자의 선택이다. */
    public int amountOf(Long categoryId) {
        return toAmount(amounts.getOrDefault(categoryId, 0L));
    }

    /** 표본을 읽어 왔는지. {@code false} 면 {@link #compare(Long)} 를 부를 수 없다. */
    public boolean isComparable() {
        return !samples.isEmpty();
    }

    /**
     * 한 카테고리의 비교 결과.
     *
     * <p><b>기록이 없는 구간은 0원으로 채운다</b>(ERD 6장) — SQL {@code group by} 는 빈
     * 구간을 아예 빼므로 여기서 메우지 않으면 표본 수가 줄어 평균이 과대평가된다.
     *
     * @throws IllegalStateException 표본을 읽지 않았을 때 ({@link #isComparable()} 확인)
     */
    public SpendingComparison compare(Long categoryId) {
        if (!isComparable()) {
            throw new IllegalStateException("표본이 없어 비교할 수 없어요 (데이터 부족).");
        }
        List<Integer> sampleAmounts = samples.stream()
                .map(sample -> toAmount(sample.getOrDefault(categoryId, 0L)))
                .toList();

        return SpendingComparison.of(amountOf(categoryId), sampleAmounts);
    }

    /** JPQL {@code sum()} 의 {@code Long} 을 명세의 int(원)로 좁힌다. */
    private static int toAmount(long sum) {
        return Math.toIntExact(sum);
    }
}
