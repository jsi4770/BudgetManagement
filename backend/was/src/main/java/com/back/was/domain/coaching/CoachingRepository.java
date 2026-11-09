package com.back.was.domain.coaching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachingRepository extends JpaRepository<Coaching, Long> {

    /**
     * COACH-01 이 새 코칭을 시작하기 전에 밀어내야 하는 세션들 (ERD 7장 "상태 변경 규칙").
     *
     * <p><b>목록으로 받는다.</b> 규칙상 사용자당 {@code IN_PROGRESS} 는 하나여야 하지만,
     * 같은 사용자가 두 탭에서 동시에 시작하면 둘이 남을 수 있다 — 유니크 제약이 없기
     * 때문이다(ERD 7장에 없고, "하나"는 이 코드가 지키는 불변식이다). {@code findTop1} 로
     * 받으면 그런 행이 영구히 {@code IN_PROGRESS} 로 남아 다음 시작마다 하나씩만 치워진다.
     */
    List<Coaching> findAllByUserIdAndStatus(Long userId, CoachingStatus status);
}
