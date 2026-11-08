package com.back.was.domain.nudge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface NudgeRepository extends JpaRepository<Nudge, Long> {

    /**
     * NDG-01 홈 노출 넛지 — 명세의 세 조건을 그대로 쿼리에 담았다.
     *
     * <ul>
     *   <li>{@code analysisStartDate} = 이번 주 월요일 → <b>지난 주 넛지는 미확인이어도
     *       노출되지 않는다</b>. 날짜 범위가 아니라 등치 비교인 건 그 값이 항상 주 월요일로
     *       저장되기 때문이다(ERD 6장).</li>
     *   <li>{@code status} = {@code UNREAD} → 이미 반응한 카드는 다시 뜨지 않는다.</li>
     *   <li>{@code updatedAt} 내림차순 {@code Top 1} → 여러 카테고리가 동시에 조건을
     *       충족하면 가장 최근에 갱신된 하나.</li>
     * </ul>
     *
     * <p>{@code userId} 가 조건에 들어가므로 남의 넛지는 애초에 보이지 않는다
     * (서버_내부_처리 공통 전제).
     */
    Optional<Nudge> findTopByUserIdAndAnalysisStartDateAndStatusOrderByUpdatedAtDesc(
            Long userId, LocalDate analysisStartDate, NudgeStatus status);
}
