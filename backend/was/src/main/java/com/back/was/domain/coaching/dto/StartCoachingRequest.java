package com.back.was.domain.coaching.dto;

/**
 * COACH-01 요청. 필드가 하나뿐이고 그것도 <b>선택</b>이다 (명세 COACH-01).
 *
 * <p>{@code nudgeId} 가 있으면 두 가지가 달라진다: 첫 AI 메시지가 그 카드의 숫자를 인용하고
 * (명세 COACH-01 예시 · {@code docs/AI_대체_문구.md} 3장), 그 넛지의 상태가
 * {@code COACHING_STARTED} 로 바뀐다(명세 9장 상태 표: "서버가 자동 변경 → 보통 NDG-03
 * 호출 불필요"). <b>코칭에는 저장되지 않는다</b> — ERD 7장이 넛지와의 FK 를 두지 않았다.
 *
 * <p>검증 애너테이션이 없다. 없는 ID·남의 ID 는 서비스가 404 로 막고(명세 0장 5)), 그
 * 판단에는 DB 가 필요하므로 Bean Validation 이 할 수 있는 일이 없다.
 *
 * @param nudgeId 넛지에서 시작한 경우 그 넛지 ID, 아니면 {@code null}
 */
public record StartCoachingRequest(
        Long nudgeId
) {
    /**
     * 본문 없이 들어온 요청을 빈 요청으로 바꾼다.
     *
     * <p>{@code POST /coachings} 는 보낼 값이 없을 수 있다 — 홈의 "AI 코치와 대화하기"는
     * 넛지 없이 시작한다. 그때 프론트가 {@code {}} 를 보내든 본문을 아예 비우든 같은 뜻이고,
     * 후자를 {@code @RequestBody} 가 400 으로 떨어뜨리는 건 명세에 없는 에러다.
     */
    public static StartCoachingRequest emptyIfNull(StartCoachingRequest request) {
        return request == null ? new StartCoachingRequest(null) : request;
    }

    public boolean hasNudgeId() {
        return nudgeId != null;
    }
}
