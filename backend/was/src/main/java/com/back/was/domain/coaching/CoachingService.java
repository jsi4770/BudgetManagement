package com.back.was.domain.coaching;

import com.back.was.domain.coaching.dto.CoachingResponse;
import com.back.was.domain.coaching.dto.ConfirmGoalRequest;
import com.back.was.domain.coaching.dto.ConfirmGoalResponse;
import com.back.was.domain.coaching.dto.SendMessageRequest;
import com.back.was.domain.coaching.dto.SendMessageResponse;
import com.back.was.domain.coaching.dto.StartCoachingRequest;
import org.springframework.stereotype.Service;

/**
 * 명세 10장 AI 코칭 — <b>아직 구현되지 않았다.</b>
 *
 * <p>비어 있는 동안 모든 메서드가 {@link UnsupportedOperationException} 을 던져 500
 * {@code INTERNAL_ERROR} 가 된다. 구현할 때 주의: COACH-03 은 Gemini 가 실패해도 200 +
 * {@code isFallback: true} 여야 하고(명세 0장 4) 각주), COACH-01 의 첫 메시지도 AI 실패로
 * 에러를 내면 안 된다({@code docs/AI_대체_문구.md}).
 *
 * <p>규칙은 {@code docs/AI_코칭_소비분석_인수인계.md} 1장 ③, 원래 구현은
 * {@code git show backup/before-handover:./src/main/java/com/back/was/domain/coaching/CoachingService.java}.
 */
@Service
public class CoachingService {

    /** COACH-01 코칭 시작. 진행 중 코칭은 {@code CANCELED} 로 밀려난다. */
    public CoachingResponse start(Long userId, StartCoachingRequest request) {
        throw new UnsupportedOperationException("COACH-01 은 아직 구현되지 않았어요.");
    }

    /** COACH-02 코칭 조회(재진입). 종료된 세션도 열린다. */
    public CoachingResponse getCoaching(Long userId, Long id) {
        throw new UnsupportedOperationException("COACH-02 는 아직 구현되지 않았어요.");
    }

    /** COACH-03 메시지 전송 → AI 응답. */
    public SendMessageResponse sendMessage(Long userId, Long id, SendMessageRequest request) {
        throw new UnsupportedOperationException("COACH-03 은 아직 구현되지 않았어요.");
    }

    /** COACH-04 목표 확정 → 챌린지 생성. */
    public ConfirmGoalResponse confirmGoal(Long userId, Long id, ConfirmGoalRequest request) {
        throw new UnsupportedOperationException("COACH-04 는 아직 구현되지 않았어요.");
    }
}
