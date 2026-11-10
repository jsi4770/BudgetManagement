package com.back.was.domain.ai;

import com.back.was.domain.category.dto.CategoryResponse;

import java.util.List;

/**
 * COACH-03 의 AI 응답을 만드는 데 필요한 것 — <b>대화 전체와 고를 수 있는 카테고리</b>.
 *
 * <p>{@code conversation} 에는 방금 받은 사용자 메시지까지 들어 있고 {@code createdAt}
 * 오름차순이다. 마지막 줄이 답해야 할 질문이다.
 *
 * <p>{@code categories} 는 {@code TextParseCommand}(TX-07)와 같은 이유로 들어간다: 모델이
 * 제안할 카테고리를 <b>DB 에 있는 것 중에서</b> 골라야 하고, 어댑터는 DB 를 볼 수 없다.
 * 지출 카테고리만 들어간다 — 절약 목표를 수입 카테고리에 걸 수는 없다.
 *
 * <p>프롬프트와 구조화 출력 스키마는 <b>AI 담당 몫</b>이다(서버_내부_처리 8장). 이
 * 레코드는 그 프롬프트가 필요한 재료의 목록이고, 백엔드가 책임지는 것은
 * {@link CoachingReply} 의 모양이다.
 *
 * @param username     말을 걸 이름
 * @param conversation 지금까지의 대화 (마지막 줄 = 방금 받은 사용자 메시지)
 * @param categories   제안 대상이 될 수 있는 지출 카테고리
 */
public record CoachingReplyCommand(
        String username,
        List<CoachingTurn> conversation,
        List<CategoryResponse> categories
) {
}
