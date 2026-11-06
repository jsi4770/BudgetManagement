package com.back.was.domain.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    /** AUTH-02 응답의 {@code hasBudget} — false 면 프론트가 초기 설정 화면으로 보낸다. */
    boolean existsByUserId(Long userId);

    /** BUDGET-01·02. 복합 UNIQUE {@code (USER_ID, YEAR_MONTH)} 라 최대 한 건이다. */
    Optional<Budget> findByUserIdAndYearMonth(Long userId, String yearMonth);

    /**
     * ERD 3장 자동 복사의 원본: 주어진 연월보다 <b>앞선</b> 행 중 가장 최근 것.
     *
     * <p>"지난 달"이 아니라 "가장 최근"인 이유는 장기 미사용자에게 지난 달 행이 없을 수
     * 있기 때문이다(ERD 3장). 요청한 달 이후의 행은 제외한다 — BUDGET-02 로 미래 달을
     * 먼저 저장했더라도 이번 달 예산이 미래 값을 끌어오면 안 된다.
     */
    Optional<Budget> findTopByUserIdAndYearMonthLessThanOrderByYearMonthDesc(Long userId, String yearMonth);
}
