package com.back.was.domain.merchant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LocalMerchantRepository extends JpaRepository<LocalMerchant, Long> {

    List<LocalMerchant> findByAltTextIn(Collection<String> altTexts);

    long countByRegionCode(String regionCode);

    /** 상호 부분 일치. {@code q} 는 빈 문자열이면 전체다 — null 을 쓰지 않는 이유는 PostgreSQL 이 타입 없는 null 을 못 읽어서다. */
    Page<LocalMerchant> findByRegionCodeAndNameContainingIgnoreCase(String regionCode, String q, Pageable pageable);

    Page<LocalMerchant> findByRegionCodeAndMerchantGroupAndNameContainingIgnoreCase(
            String regionCode, MerchantGroup merchantGroup, String q, Pageable pageable);

    /** 코칭 추천용: 한 묶음의 가맹점 중 앞쪽 일부. 정렬은 호출자가 Pageable 로 정한다. */
    List<LocalMerchant> findByRegionCodeAndMerchantGroupIn(
            String regionCode, Collection<MerchantGroup> groups, Pageable pageable);

    /** 이번 동기화에서 확인되지 않은 행(폐업·해지로 API 에서 빠진 것)을 지운다. */
    @Modifying
    @Query("delete from LocalMerchant m where m.regionCode = :regionCode and m.syncedAt < :before")
    int deleteStale(@Param("regionCode") String regionCode, @Param("before") LocalDateTime before);
}
