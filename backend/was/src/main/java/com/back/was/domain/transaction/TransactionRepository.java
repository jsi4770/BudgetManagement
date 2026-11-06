package com.back.was.domain.transaction;

import com.back.was.domain.category.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

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
}
