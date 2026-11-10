package com.back.was.domain.coaching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachingMessageRepository extends JpaRepository<CoachingMessage, Long> {

    /**
     * 대화 전체를 순서대로 (명세 COACH-01·02 {@code messages}: "{@code createdAt} 오름차순").
     *
     * <p><b>ID 를 2차 정렬 기준으로 쓴다.</b> COACH-03 은 사용자 메시지와 AI 응답을 한
     * 요청에서 연달아 저장하는데, {@code CREATED_AT} 이 초 단위라 두 행의 값이 같아질 수
     * 있다. 그러면 질문과 대답의 순서가 뒤집혀 복원되고, 그건 화면에서 바로 보이는 버그다.
     * ID 는 증가하므로 같은 시각 안의 순서를 정한다.
     *
     * <p>{@code coachingId} 로만 찾는다 — 소유권은 코칭 쪽에서 이미 확인한 뒤에 부른다
     * (COACH-02 의 {@code findByIdAndUserId}).
     */
    List<CoachingMessage> findByCoachingIdOrderByCreatedAtAscIdAsc(Long coachingId);
}
