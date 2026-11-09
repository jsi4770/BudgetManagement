package com.back.was.domain.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

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
