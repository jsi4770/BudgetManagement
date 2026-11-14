package com.back.was.domain.coaching.dto;

import com.back.was.domain.challenge.Challenge;
import com.back.was.domain.coaching.Coaching;
import com.back.was.global.support.KstTime;

import java.time.OffsetDateTime;

/**
 * COACH-04 응답 (명세 COACH-04).
 *
 * <p>챌린지의 내용을 되돌려주지 않는다 — 방금 보낸 값이라 프론트가 이미 알고 있고, 전체는
 * CHL-02 로 조회한다. 여기서 알려줄 것은 <b>새 챌린지의 ID</b>, <b>코칭이 끝났다는 사실</b>,
 * 그리고 <b>그 시각</b>뿐이다.
 *
 * <p>{@code challengeCreatedAt} 이 곧 <b>코칭 종료 시각</b>이다(ERD 7장 "시작·종료 시각"
 * 표 — COACHING 에 종료 시각 컬럼이 없어서 CHALLENGE 의 생성 시각이 그 역할을 한다).
 * 그래서 이 필드가 응답에 있는 것이고, 이름만 보고 "그냥 생성 시각"으로 지우면 종료 시각을
 * 알 방법이 사라진다.
 *
 * <p>{@code coaching} 을 {@code {id, status}} 로 내려 주는 것도 명세의 모양이다. 상태를
 * 함께 보내므로 프론트는 COACH-02 를 다시 부르지 않고 화면을 종료 상태로 바꿀 수 있다.
 */
public record ConfirmGoalResponse(
        Long challengeId,
        CoachingRef coaching,
        OffsetDateTime challengeCreatedAt
) {
    /** 명세 COACH-04 의 {@code coaching} 조각. {@code CoachingResponse} 와는 쓰임이 다르다. */
    public record CoachingRef(
            Long id,
            String status
    ) {
        public static CoachingRef from(Coaching coaching) {
            return new CoachingRef(coaching.getId(), coaching.getStatus().name());
        }
    }

    public static ConfirmGoalResponse of(Challenge challenge, Coaching coaching) {
        return new ConfirmGoalResponse(
                challenge.getId(),
                CoachingRef.from(coaching),
                // ERD 는 _AT 를 시간대 없는 DATETIME 으로 두지만 명세 0장 2)는 오프셋을 요구한다
                KstTime.toOffset(challenge.getCreatedAt()));
    }
}
