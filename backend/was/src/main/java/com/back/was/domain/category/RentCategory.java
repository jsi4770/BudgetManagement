package com.back.was.domain.category;

/**
 * ERD 4장 "월세 처리 (코드 상수, 별도 컬럼 없음)".
 *
 * <p>월세는 한 달에 한 번 큰 금액이 나가서 그대로 비교하면 평균·Z-score 가 크게 흔들린다.
 * 그래서 테이블에 플래그 컬럼을 두지 않고 <b>ID 5 를 코드 상수로</b> 참조하라는 것이 ERD
 * 4장의 결정이다. {@link CategorySeeder} 가 시드 순서로 그 ID 를 보장한다.
 *
 * <p>제외 여부는 쓰는 곳마다 다르다 (ERD 4장 표):
 *
 * <table border="1">
 *   <caption>월세 처리</caption>
 *   <tr><th>위치</th><th>처리</th></tr>
 *   <tr><td>넛지 판정 (서버_내부_처리 3장)</td><td>제외</td></tr>
 *   <tr><td>ANL-02 주간 지출 추이</td><td>제외</td></tr>
 *   <tr><td>ANL-03 {@code WEEK}</td><td>제외 (합계·항목 전부)</td></tr>
 *   <tr><td>ANL-03 {@code MONTH} 총지출</td><td><b>포함</b> (홈 총지출과 같은 값이어야 함)</td></tr>
 *   <tr><td>ANL-03 {@code MONTH} 항목의 평균·증가율</td><td>제외 ({@code null})</td></tr>
 *   <tr><td>ANL-01 월별 요약</td><td><b>포함</b></td></tr>
 * </table>
 */
public final class RentCategory {

    /** ERD 4장 시드 표의 다섯 번째 행(월세). 시드 순서가 ID 를 고정한다. */
    public static final long ID = 5L;

    private RentCategory() {
    }

    public static boolean isRent(Long categoryId) {
        return categoryId != null && categoryId == ID;
    }
}
