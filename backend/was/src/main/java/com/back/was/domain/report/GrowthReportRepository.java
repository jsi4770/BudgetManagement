package com.back.was.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GrowthReportRepository extends JpaRepository<GrowthReport, Long> {

    /**
     * CHL-03 성장 리포트. <b>명세 CHL-03 의 세 가지 404 가 이 한 쿼리로 합쳐진다</b>:
     * 없는 챌린지, 남의 챌린지, 그리고 <b>진행 중이라 리포트가 아직 없는</b> 챌린지.
     * 모두 "결과 없음"으로 떨어지므로 서비스는 세 경우를 구분하지 않고 404 를 던진다 —
     * 구분하면 ID 를 훑어 남의 챌린지 존재 여부를 알 수 있다(명세 0장 5)).
     *
     * <p>사용자까지 두 단계를 거친다: {@code report.challenge.coaching.user.id}. CHALLENGE 에
     * {@code USER_ID} 가 없고(ERD 9장) GROWTH_REPORT 에도 없으므로 코칭까지 가야 주인을
     * 안다. 파생 쿼리 이름으로 적으면
     * {@code findByChallengeIdAndChallengeCoachingUserId} 가 되는데, 두 단계 조인이 이름에
     * 묻히는 쪽보다 JPQL 로 보이는 쪽이 낫다.
     *
     * <p>{@code join fetch} 로 챌린지와 카테고리를 함께 읽는다. 응답이 챌린지의 제목·목표·
     * 실적을 모두 쓰므로 지연 로딩이면 쿼리가 세 번으로 쪼개진다.
     */
    @Query("""
            select r from GrowthReport r
            join fetch r.challenge ch
            join fetch ch.category
            where ch.id = :challengeId
              and ch.coaching.user.id = :userId
            """)
    Optional<GrowthReport> findByChallengeIdAndUserId(@Param("challengeId") Long challengeId,
                                                      @Param("userId") Long userId);

    /**
     * 같은 챌린지에 리포트를 두 번 만들지 않기 위한 확인 (서버_내부_처리 4장: 판정은 한 번).
     *
     * <p>{@code CHALLENGE_ID} 의 UNIQUE 가 최종 방어선이지만, 거기까지 가면 스케줄러 한
     * 번의 실행이 예외로 끝나 <b>뒤에 남은 챌린지들이 판정되지 않는다</b>. 그래서 미리
     * 건너뛴다.
     */
    boolean existsByChallengeId(Long challengeId);
}
