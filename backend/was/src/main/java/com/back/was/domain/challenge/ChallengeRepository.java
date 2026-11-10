package com.back.was.domain.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    /**
     * CHL-02 챌린지 상세. <b>{@code id} 만으로 찾지 않는다</b> — 조건에 사용자가 함께
     * 들어가야 남의 챌린지가 "없음"과 구분되지 않고 404 가 된다(명세 0장 5)). 소유권을
     * 서비스에서 뒤늦게 비교하면 깜빡한 곳이 그대로 정보 노출이 된다.
     *
     * <p>이름의 {@code CoachingUserId} 가 조인이다 — CHALLENGE 에 {@code USER_ID} 가 없어서
     * {@code COACHING.USER_ID} 로 경유한다(ERD 9장). {@code existsByCoachingUserIdAndStatus}
     * 와 같은 경로다.
     *
     * <p><b>상태로 걸러내지 않는다.</b> 종료된 챌린지도 열려야 한다 — 명세 CHL-02 에 그런
     * 조건이 없고, 끝난 챌린지를 다시 보는 것(CHL-03 성장 리포트로 가는 길)이 정상적인
     * 열람이다. NDG-01 의 노출 조건이 NDG-02 에 걸리지 않는 것과 같다.
     *
     * <p>CHL-01 과 달리 {@code join fetch} 가 없다. 한 건이라 카테고리 지연 로딩이 쿼리
     * 하나를 더하는 것뿐이고(N+1 이 아니다), 파생 쿼리 이름이 조인 경로를 그대로 보여 준다.
     */
    Optional<Challenge> findByIdAndCoachingUserId(Long id, Long userId);

    /**
     * CHL-01 챌린지 목록. {@code status} 가 {@code null} 이면 전체다 (명세 CHL-01
     * "없으면 전체") — TX-02 의 선택 필터와 같은 {@code (:x is null or ...)} 방식이다.
     *
     * <p>사용자 범위는 {@code ch.coaching.user.id} 로 들어간다 (CHALLENGE 에
     * {@code USER_ID} 가 없다 — ERD 9장). 조건이 쿼리에 있으므로 남의 챌린지가 목록에
     * 섞일 경로가 없다.
     *
     * <p><b>정렬은 명세에 없어서 여기서 정했다</b>: 최신 확정 순
     * ({@code createdAt} 내림차순). {@code CREATED_AT} 은 확정 시각이고(ERD 9장) 한 번
     * 정해지면 바뀌지 않아서, 실적이 갱신돼도 목록의 순서가 흔들리지 않는다. 같은 초에
     * 확정된 두 건의 순서는 {@code id} 가 정한다 — 없으면 목록 순서가 실행마다 달라진다.
     *
     * <p>{@code join fetch} 로 카테고리를 함께 읽어 응답 매핑 때의 N+1 을 막는다(TX-02 와
     * 같은 이유). 코칭은 가져오지 않는다 — 응답에 들어가지 않고 조건으로만 쓰인다.
     *
     * <p>페이지네이션이 없다. 명세 CHL-01 이 {@code data} 를 바로 배열로 두고 페이지 정보를
     * 요구하지 않는다(CAT-01 과 같은 모양). 챌린지는 한 번에 하나씩 진행하므로 한 사용자의
     * 누적 건수가 빠르게 늘지 않는다 — 늘어나면 명세에 페이지 정보를 추가하는 것이 먼저다.
     */
    @Query("""
            select ch from Challenge ch
            join fetch ch.category
            where ch.coaching.user.id = :userId
              and (:status is null or ch.status = :status)
            order by ch.createdAt desc, ch.id desc
            """)
    List<Challenge> findAllByUserId(@Param("userId") Long userId,
                                    @Param("status") ChallengeStatus status);

    /**
     * 종료 판정 대상 — 서버_내부_처리 4장: {@code END_DATE < 오늘} AND
     * {@code STATUS = 'IN_PROGRESS'}.
     *
     * <p><b>조건 기반이라 밀린 것까지 처리된다</b>(4장). "어제 끝난 것"이 아니라 "끝났는데
     * 아직 판정되지 않은 전부"를 집으므로, 서버가 며칠 꺼져 있었어도 다음 실행 한 번으로
     * 따라잡는다. 그래서 스케줄러가 실패한 날을 따로 기억할 필요가 없다.
     *
     * <p>{@code endDate < today} 이므로 <b>종료일 당일은 아직 판정하지 않는다</b> — 그날
     * 자정까지 기록이 더 들어올 수 있다. 판정은 종료일 다음 날 00:05 다.
     *
     * <p>오래된 것부터 처리한다. 순서가 결과를 바꾸지는 않지만(챌린지끼리 영향이 없다)
     * 로그를 시간순으로 읽을 수 있다.
     */
    List<Challenge> findAllByStatusAndEndDateBeforeOrderByEndDateAsc(
            ChallengeStatus status, LocalDate endDate);

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
