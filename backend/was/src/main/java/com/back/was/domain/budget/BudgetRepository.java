package com.back.was.domain.budget;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    /** AUTH-02 응답의 {@code hasBudget} — false 면 프론트가 초기 설정 화면으로 보낸다. */
    boolean existsByUserId(Long userId);
}
