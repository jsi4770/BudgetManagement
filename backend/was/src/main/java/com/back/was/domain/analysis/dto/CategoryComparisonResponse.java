package com.back.was.domain.analysis.dto;

import com.back.was.domain.analysis.AnalysisPeriod;
import com.back.was.global.support.DateRange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ANL-03 응답 (소비 분석 – 카테고리별 비교).
 *
 * <p>{@code items} 는 <b>지출 카테고리 전부</b>를 ID 순으로 담는다 ({@code WEEK} 는 월세
 * 제외). 이번 기간에 지출이 0원인 카테고리도 들어간다 — 평소 쓰던 항목이 0원이 된 것도
 * "평소 대비 비교" 화면의 정보이고(증가율 −100%), docs/시연_데이터.md 6-1·6-3 의 표가
 * 0원인 기타까지 포함해 다섯·여섯 줄을 모두 보여 준다. 명세의 응답 예시가 두 줄뿐인 것은
 * 생략 표기다(월간 예시도 6개 중 2개만 적혀 있다).
 *
 * <p><b>Z-score 는 응답에 없다</b>(명세 ANL-03). 화면에는 금액과 증가율만 쓰고, Z 는 넛지
 * 판정과 발표 근거용으로 NUDGE 테이블에만 남는다.
 */
public record CategoryComparisonResponse(
        String period,
        LocalDate startDate,
        LocalDate endDate,
        boolean dataSufficient,
        int totalAmount,
        List<Item> items
) {
    /**
     * 카테고리 한 줄.
     *
     * @param averageAmount 평소 같은 기간 평균. <b>{@code null}</b> 이면 비교하지 않은 것 —
     *                      데이터 부족({@code dataSufficient: false}) 또는 월세({@code MONTH})
     * @param growthRate    평균 대비 증가율(%). 비교하지 않았거나 평균이 0원이면 {@code null}
     * @param isAnomaly     {@code WEEK} 의 넛지 생성 조건과 동일. {@code MONTH} ·데이터 부족
     *                      ·월세는 항상 {@code false}
     */
    public record Item(
            CategoryRef category,
            int amount,
            Integer averageAmount,
            BigDecimal growthRate,
            boolean isAnomaly
    ) {
    }

    /**
     * 항목의 카테고리. 명세 ANL-03 은 {@code {id, name}} 만 쓴다 — CAT-01·TX-01 의
     * {@code CategoryResponse} 와 달리 {@code type} 이 없다. 항목이 전부 지출이라 type 이
     * 의미가 없어서인데, 키 구성이 다르므로 그 레코드를 재사용하지 않는다
     * ({@code MeResponse} vs {@code UserResponse} 와 같은 판단).
     */
    public record CategoryRef(Long id, String name) {
    }

    public static CategoryComparisonResponse of(AnalysisPeriod period, DateRange analysisRange,
                                                boolean dataSufficient, List<Item> items) {
        return new CategoryComparisonResponse(
                period.name(),
                analysisRange.start(),
                analysisRange.end(),
                dataSufficient,
                items.stream().mapToInt(Item::amount).sum(),
                List.copyOf(items));
    }
}
