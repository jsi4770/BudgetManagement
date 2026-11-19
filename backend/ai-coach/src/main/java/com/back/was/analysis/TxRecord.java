package com.back.was.analysis;

import java.time.LocalDate;

/** 분석에 필요한 거래 한 건 (TRANSACTION 테이블에서 이 4개만 꺼내면 됨) */
public record TxRecord(Long categoryId, long amount, LocalDate date, String memo) {}
