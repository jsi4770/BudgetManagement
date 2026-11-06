package com.back.was.global.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * ERD 0장: 서버·DB·스케줄러 모두 KST.
 *
 * <p>엔티티의 {@code _AT} 컬럼은 ERD 대로 시간대 없는 {@code DATETIME}
 * ({@link LocalDateTime})인데, API 명세서 0장 2)는 날짜+시간을 <b>시간대 포함</b>
 * ISO 8601 ({@code 2026-11-04T19:30:00+09:00})로 내보내라고 한다. 그 간극을 응답
 * 직렬화 직전에 메운다 — 저장은 KST 벽시계 그대로, 응답은 오프셋을 붙여서.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    /** 저장된 KST 벽시계 값에 {@code +09:00} 오프셋을 붙인다. */
    public static OffsetDateTime toOffset(LocalDateTime kstWallClock) {
        return kstWallClock == null ? null : kstWallClock.atZone(ZONE).toOffsetDateTime();
    }

    /**
     * "오늘"의 단일 정의. 서버 JVM 의 기본 시간대가 KST 가 아닐 수 있으므로
     * {@code LocalDate.now()} 를 직접 쓰지 않는다 — 미래 날짜 검증(TX-01)과 주·월 경계
     * 계산이 모두 이 값을 기준으로 해야 한다.
     */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
