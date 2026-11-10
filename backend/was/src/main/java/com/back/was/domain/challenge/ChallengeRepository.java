package com.back.was.domain.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    /**
     * 거래 후처리 2번의 재계산 대상 — 서버_내부_처리 2장의 세 조건을 그대로 담았다:
     * 해당 사용자의 <b>{@code IN_PROGRESS}</b> 챌린지 중 <b>카테고리가 일치</b>하고
     * <b>거래 날짜가 {@code START_DATE} ~ {@code END_DATE} 안</b>에 있는 것.
     *
     * <p>{@code status} 를 조건에 넣는 것이 "종료된 챌린지는 거래가 바뀌어도 재계산하지
     * 않는다"(서버_내부_처리 1장)의 구현이다. 서비스에서 뒤늦게 걸러내면 깜빡한 곳이
     * 그대로 확정된 결과를 덮어쓴다.
     *
     * <p><b>목록으로 받는다.</b> "사용자당 진행 중 1개"(ERD 9장)는 COACH-04 가 지키는
     * 불변식이지 DB 제약이 아니라, 동시 확정으로 두 건이 남을 수 있다. 하나만 집어 오면
     * 나머지 실적이 영구히 멈춘다.
     *
     * <p>이름을 파생 쿼리로 적으면
     * {@code findByCoachingUserIdAndStatusAndCategoryIdAndStartDateLessThanEqualAnd...} 가
     * 되어 읽을 수 없고, 날짜 두 개의 비교 방향을 이름만 보고 검증하기도 어렵다. 상태를
     * 파라미터로 받는 이유는 JPQL 에 enum 상수를 FQCN 리터럴로 적지 않기 위함이다(TX-03 의
     * 집계 쿼리와 같은 판단).
     *
     * @param date 거래의 날짜. 양끝을 포함한다 — {@code END_DATE} 당일 거래도 챌린지 실적이다
     */
    @Query("""
            select ch from Challenge ch
            where ch.coaching.user.id = :userId
              and ch.status = :status
              and ch.category.id = :categoryId
              and :date between ch.startDate and ch.endDate
            """)
    List<Challenge> findCovering(@Param("userId") Long userId,
                                 @Param("status") ChallengeStatus status,
                                 @Param("categoryId") Long categoryId,
                                 @Param("date") LocalDate date);

    /**
     * "이 사용자에게 이 상태의 챌린지가 있는지" — ERD 9장 "등록 규칙"(사용자당 진행 중
     * 1개)을 검사하는 유일한 경로다.
     *
     * <p><b>이름의 {@code CoachingUserId} 가 조인이다.</b> CHALLENGE 에
     * {@code USER_ID} 가 없어서 {@code COACHING.USER_ID} 로 경유하며
     * ({@code ch.coaching.user.id}), 서버_내부_처리 6장이 명시적으로 그렇게 적어 두었다
     * ("진행 중 챌린지 검사는 COACHING 을 JOIN 해 사용자 단위로 확인"). 이 경로를 끊고
     * CHALLENGE 에 {@code USER_ID} 를 더하면 코칭의 주인과 챌린지의 주인이 어긋날 수 있는
     * 상태가 생긴다.
     *
     * <p>상태를 인자로 받는 건 JPQL 에 enum 상수를 FQCN 리터럴로 적지 않기 위함이다 —
     * 그 리터럴은 어느 컴파일러도 검사하지 않는다(TX-03 의 집계 쿼리와 같은 판단).
     *
     * <p>호출자는 언제나 {@code IN_PROGRESS} 를 넘긴다. 종료된 챌린지는 몇 개든 막지
     * 않는다 — 막는 것은 지금 달리고 있는 하나뿐이고, 그래서 과거 챌린지가 쌓여도
     * 사용자가 잠기지 않는다.
     */
    boolean existsByCoachingUserIdAndStatus(Long userId, ChallengeStatus status);
}
