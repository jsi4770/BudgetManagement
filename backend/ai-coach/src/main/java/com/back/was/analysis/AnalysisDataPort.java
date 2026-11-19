package com.back.was.analysis;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 분석·넛지가 DB 에서 읽어야 하는 것들. 팀 엔티티/리포지토리에 맞춰 AnalysisDataPortAdapter 에 구현.
 * (모든 분석은 조회 시점에 계산, 별도 저장 없음 - 명세 8)
 */
public interface AnalysisDataPort {

    /** from ~ to (둘 다 포함) 사이의 해당 사용자 거래 전부 (수입·지출 모두) */
    List<TxRecord> findTransactions(Long userId, LocalDate from, LocalDate to);

    /** 가입일 (USER.createdAt 의 KST 날짜) */
    LocalDate signupDate(Long userId);

    /** 해당 월 예산 (BUDGET-01 규칙: 이번 달 없으면 최근 예산 복사). 과거 달에 없으면 empty */
    Optional<Long> monthlyBudget(Long userId, YearMonth yearMonth);
}
