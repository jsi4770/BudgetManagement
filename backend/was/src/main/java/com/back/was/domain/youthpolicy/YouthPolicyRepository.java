package com.back.was.domain.youthpolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface YouthPolicyRepository extends JpaRepository<YouthPolicy, Long> {

    List<YouthPolicy> findByPlcyNoIn(Collection<String> plcyNos);

    /** 이 지역이 대상이고 아직 신청할 수 있는(상시이거나 마감 전) 정책. 나이는 호출자가 거른다. */
    @Query("""
            select p from YouthPolicy p
            where p.zipCodes like concat('%,', :zip, ',%')
              and (p.applyEnd is null or p.applyEnd >= :today)
            """)
    List<YouthPolicy> findOpenForRegion(@Param("zip") String zip, @Param("today") LocalDate today);

    @Modifying
    @Query("delete from YouthPolicy p where p.syncedAt < :before")
    int deleteStale(@Param("before") LocalDateTime before);

    long count();
}
