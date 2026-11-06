package com.back.was.domain.category;

/**
 * ERD 4장 CATEGORY.TYPE — 수입/지출 구분.
 *
 * <p>거래의 수입·지출은 별도 컬럼이 아니라 카테고리의 이 값으로 결정된다
 * (ERD 5장 TRANSACTION.CATEGORY_ID 주석).
 */
public enum CategoryType {
    INCOME,
    EXPENSE
}
