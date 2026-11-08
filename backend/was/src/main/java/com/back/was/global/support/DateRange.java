package com.back.was.global.support;

import java.time.LocalDate;

/**
 * 분석 기간 한 구간. 시작·종료 <b>둘 다 포함</b>이다 (DB 의 {@code between} 과 같은 의미).
 *
 * <p>분석 API 는 날짜 쌍을 계속 주고받는다 — ANL-01 의 "1일 ~ 말일", ANL-02 의 주별 막대,
 * ANL-03 의 "월요일 ~ 오늘"과 직전 4주 표본 구간. {@code (from, to)} 두 인자를 그때마다
 * 따로 넘기면 순서를 뒤집어도 컴파일이 통과하므로, 한 타입으로 묶어 역순을 생성 시점에
 * 막는다.
 *
 * <p>이 타입은 날짜만 다룬다 — 시각이 필요한 곳은 없다(명세 0장 2) 날짜 필드는
 * {@code LocalDate}). "오늘"의 기준은 {@link KstTime#today()} 이다.
 */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("기간의 시작·종료 날짜는 둘 다 필요해요.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "기간의 종료 날짜가 시작보다 앞설 수 없어요: %s ~ %s".formatted(start, end));
        }
    }

    /** 양끝을 포함한다 — 하루짜리 구간({@code start == end})에서도 그 날짜가 들어간다. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
