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

    /**
     * NDG-02 상세. <b>{@code id} 만으로 찾지 않는다</b> — 조건에 {@code userId} 가 함께
     * 들어가야 남의 넛지가 "없음"과 구분되지 않고 404 가 된다(명세 0장 5)). 소유권을
     * 서비스에서 뒤늦게 비교하면 깜빡한 곳이 그대로 정보 노출이 된다
     * ({@code TransactionRepository.findByIdAndUserId} 와 같은 이유).
     *
     * <p>NDG-01 과 달리 <b>주·상태로 걸러내지 않는다</b> — 지난 주 카드도, 이미 닫은
     * 카드도 상세는 열려야 한다. 홈 노출 조건은 NDG-01 만의 규칙이다.
     */
    Optional<Nudge> findByIdAndUserId(Long id, Long userId);

    /**
     * 판정의 upsert 대상 — 복합 UNIQUE {@code (USER_ID, CATEGORY_ID, ANALYSIS_START_DATE)}
     * 그대로라 최대 한 건이다.
     *
     * <p>두 문서는 MySQL {@code INSERT ... ON DUPLICATE KEY UPDATE} 로 적어 두었지만 이
     * 프로젝트는 Postgres(운영)·H2(테스트)다. 조회 후 있으면 수정·없으면 삽입으로 대신하고,
     * 같은 사용자의 동시 요청이 유니크 인덱스에서 부딪히는 경우는 받아들인다(거래 쓰기는
     * 사용자당 직렬에 가깝다).
     */
    Optional<Nudge> findByUserIdAndCategoryIdAndAnalysisStartDate(
            Long userId, Long categoryId, LocalDate analysisStartDate);
}
