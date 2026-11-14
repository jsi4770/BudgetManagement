package com.back.was.domain.coaching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoachingRepository extends JpaRepository<Coaching, Long> {

    /**
     * COACH-02 조회. <b>{@code id} 만으로 찾지 않는다</b> — 조건에 {@code userId} 가 함께
     * 들어가야 남의 코칭이 "없음"과 구분되지 않고 404 가 된다(명세 0장 5)). 소유권을
     * 서비스에서 뒤늦게 비교하면 깜빡한 곳이 그대로 정보 노출이 되고, 여기서 새는 것은
     * 대화 전문이다.
     *
     * <p><b>상태로 걸러내지 않는다.</b> 종료된({@code COMPLETED}·{@code CANCELED}) 세션도
     * 열려야 한다 — 자세한 이유는 {@code CoachingService.getCoaching}. COACH-03·04 가
     * 종료된 세션을 거절하는 것은 쓰기 쪽 규칙이다.
     *
     * <p>COACH-03·04 도 이 메서드를 쓴다. 그 두 곳은 받아 온 뒤
     * {@link Coaching#isEnded()} 로 409 를 추가로 판단한다.
     */
    Optional<Coaching> findByIdAndUserId(Long id, Long userId);

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
