package com.back.was.domain.transaction;

import com.back.was.domain.category.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * TX-04 상세. <b>{@code id} 만으로 찾지 않는다</b> — 조건에 {@code userId} 가 함께
     * 들어가야 남의 거래가 "없음"과 구분되지 않고 404 가 된다(명세 0장 5) ·
     * 서버_내부_처리 공통 전제). 소유권을 서비스에서 뒤늦게 비교하는 방식이면 비교를
     * 깜빡한 곳이 그대로 정보 노출이 되므로, 쿼리 자체에 범위를 박는다.
     */
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    /**
     * 넛지 문구 템플릿의 {@code {count}}("{days}일 동안 {count}번 결제했어요").
     *
     * <p>NUDGE 에는 금액만 저장되고 횟수가 없어서(ERD 6장) 문구를 만들 때 한 번 센다.
     * 양끝을 포함하는 {@code between} 이라 분석 기간의 정의와 같다.
     */
    long countByUserIdAndCategoryIdAndTransactionDateBetween(
            Long userId, Long categoryId, LocalDate from, LocalDate to);

    /**
     * TX-02 목록. {@code type} · {@code categoryId} 는 {@code null} 이면 조건에서 빠진다
     * (명세 TX-02 "없으면 전체").
     *
     * <p>정렬은 {@code Pageable} 이 아니라 쿼리에 박혀 있다 — 명세가 고정한 순서
     * ({@code transactionDate} 내림차순 → 같은 날짜는 {@code createdAt} 내림차순)이고,
     * 호출자가 바꿀 수 있는 값이 아니다. 그래서 서비스는 정렬 없는
     * {@code PageRequest.of(page, size)} 를 넘긴다.
     *
     * <p>{@code join fetch} 로 카테고리를 함께 읽어 응답 매핑 때의 N+1 을 막는다.
     * fetch join 이 있으면 Spring Data 가 count 쿼리를 자동 생성할 수 없어
     * {@code countQuery} 를 따로 적는다.
     */
    @Query(value = """
            select t from Transaction t
            join fetch t.category c
            where t.user.id = :userId
              and t.transactionDate between :from and :to
              and (:type is null or c.type = :type)
              and (:categoryId is null or c.id = :categoryId)
            order by t.transactionDate desc, t.createdAt desc
            """,
            countQuery = """
            select count(t) from Transaction t
            where t.user.id = :userId
              and t.transactionDate between :from and :to
              and (:type is null or t.category.type = :type)
              and (:categoryId is null or t.category.id = :categoryId)
            """)
    Page<Transaction> search(@Param("userId") Long userId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("type") CategoryType type,
                             @Param("categoryId") Long categoryId,
                             Pageable pageable);

    /**
     * TX-03 달력용 일별 합계. 하루에 수입·지출이 섞여 있어도 한 줄로 나온다.
     *
     * <p>{@code group by} 덕분에 <b>거래가 있는 날짜만</b> 결과에 들어간다(명세 TX-03) —
     * 빈 날짜를 0 으로 채우지 않는다.
     *
     * <p>수입/지출 구분은 거래가 아니라 카테고리 TYPE 이므로(ERD 5장) 두 값을
     * 파라미터로 받아 {@code case} 로 가른다. JPQL 에 enum 리터럴을 FQCN 으로 적는
     * 방법도 있지만, 그 문자열은 컴파일 타임에 검증되지 않아 패키지를 옮기면 조용히
     * 깨진다.
     */
    @Query("""
            select new com.back.was.domain.transaction.DailyAmountSum(
                t.transactionDate,
                sum(case when t.category.type = :income then t.amount else 0 end),
                sum(case when t.category.type = :expense then t.amount else 0 end))
            from Transaction t
            where t.user.id = :userId
              and t.transactionDate between :from and :to
            group by t.transactionDate
            order by t.transactionDate
            """)
    List<DailyAmountSum> sumDailyAmounts(@Param("userId") Long userId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("income") CategoryType income,
                                         @Param("expense") CategoryType expense);

    /**
     * ANL-01 월별 요약의 수입·지출 합계. 월세도 포함한다(ERD 4장 — 홈 총지출이 기준값).
     *
     * <p>집계 함수만 있고 {@code group by} 가 없으므로 결과는 <b>항상 한 줄</b>이다 —
     * 거래가 하나도 없으면 {@code sum()} 이 {@code null} 인 줄이 나오고, 그대로 레코드
     * 생성자에 들어가면 {@code long} 언박싱에서 NPE 가 난다. {@code coalesce} 는 그 방지용
     * 이고, 덕분에 "거래 없음"이 0원으로 내려온다 (명세 ANL-01 에 빈 달 404 는 없다).
     */
    @Query("""
            select new com.back.was.domain.transaction.AmountSummary(
                coalesce(sum(case when t.category.type = :income then t.amount else 0 end), 0L),
                coalesce(sum(case when t.category.type = :expense then t.amount else 0 end), 0L))
            from Transaction t
            where t.user.id = :userId
              and t.transactionDate between :from and :to
            """)
    AmountSummary sumAmounts(@Param("userId") Long userId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("income") CategoryType income,
                             @Param("expense") CategoryType expense);

    /**
     * ANL-02 주간 막대의 재료 — 기간 안의 <b>일별 지출</b> 합계.
     *
     * <p>주 단위로 {@code group by} 하지 않는 이유는 {@link DailyAmount} 에 적어 두었다
     * (주 경계의 정의를 DB 함수로 흘리지 않는다). 거래가 없는 날은 결과에서 빠지므로
     * 주별로 담을 때 빈 주가 0원이 되는 건 호출자 몫이다.
     *
     * <p>{@code excludedCategoryId} 가 {@code null} 이면 제외 없이 전부 더한다. ANL-02 는
     * 항상 월세({@link com.back.was.domain.category.RentCategory#ID})를 넣어 호출한다.
     */
    @Query("""
            select new com.back.was.domain.transaction.DailyAmount(
                t.transactionDate, sum(t.amount))
            from Transaction t
            where t.user.id = :userId
              and t.transactionDate between :from and :to
              and t.category.type = :expense
              and (:excludedCategoryId is null or t.category.id <> :excludedCategoryId)
            group by t.transactionDate
            order by t.transactionDate
            """)
    List<DailyAmount> sumDailyExpenses(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("expense") CategoryType expense,
                                       @Param("excludedCategoryId") Long excludedCategoryId);

    /**
     * ANL-03 항목과 넛지 판정이 공유하는 집계 — 기간 안의 <b>카테고리별 지출</b> 합계.
     *
     * <p>분석 기간에도, 표본 구간(직전 4주 / 직전 3개월) 각각에도 같은 쿼리를 쓴다.
     * 명세 ANL-03·서버_내부_처리 3장이 "넛지와 같은 계산 함수"를 요구하므로 평균을 만드는
     * 입력이 한 종류여야 한다.
     *
     * <p>수입 카테고리는 조건에서 빠진다(비교 대상은 지출뿐). {@code excludedCategoryId} 로
     * 월세를 빼는데, 빼는지 여부는 호출하는 쪽(주간/월간)에 따라 다르다 —
     * {@link com.back.was.domain.category.RentCategory} 표 참고.
     *
     * <p>거래가 없는 카테고리는 줄이 아예 없다. "기록 없는 주는 0원으로 포함"(ERD 6장)은
     * 그래서 호출자가 채워야 한다 — 빠뜨리면 표본이 4개 미만이 되어 평균이 과대평가된다.
     */
    @Query("""
            select new com.back.was.domain.transaction.CategoryAmountSum(
                c.id, c.name, sum(t.amount))
            from Transaction t
            join t.category c
            where t.user.id = :userId
              and t.transactionDate between :from and :to
              and c.type = :expense
              and (:excludedCategoryId is null or c.id <> :excludedCategoryId)
            group by c.id, c.name
            order by c.id
            """)
    List<CategoryAmountSum> sumExpensesByCategory(@Param("userId") Long userId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to,
                                                  @Param("expense") CategoryType expense,
                                                  @Param("excludedCategoryId") Long excludedCategoryId);
}
