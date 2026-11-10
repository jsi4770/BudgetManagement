package com.back.was.domain.challenge;

import com.back.was.domain.challenge.dto.ChallengeResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 명세 11장 챌린지(CHALLENGE). 챌린지는 항상 토큰 주인의 것이라 경로에 사용자 ID 가 없다 —
 * 주인은 {@code COACHING.USER_ID} 를 거쳐 안다(ERD 9장).
 *
 * <p>목록이 페이지네이션 없이 {@code data} 바로 배열인 것은 명세 CHL-01 의 모양이다
 * (CAT-01 과 같다). 그래서 {@code PageResponse} 를 쓰지 않는다.
 */
@RestController
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    /**
     * CHL-01 챌린지 목록.
     *
     * <p>{@code status} 는 선택이다. {@code IN_PROGRESS}/{@code SUCCESS}/{@code FAILED} 가
     * 아닌 값은 Spring 의 enum 변환이 실패해
     * {@code MethodArgumentTypeMismatchException} → 400 {@code VALIDATION_ERROR} 로
     * 떨어진다(CAT-01 의 {@code type} 과 같은 구조).
     *
     * <p>검증 애너테이션이 없으므로 {@code @ModelAttribute} 레코드로 묶지 않았다 — 묶어야
     * 하는 건 제약이 붙은 쿼리 파라미터다(제약을 핸들러 파라미터에 직접 달면 Spring 7 의
     * 메서드 검증이 {@code HandlerMethodValidationException} 을 던져 500 이 된다).
     * TX-03 의 {@code yearMonth} 와 같은 판단이다.
     */
    @GetMapping
    public ApiResponse<List<ChallengeResponse>> getChallenges(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) ChallengeStatus status) {
        return ApiResponse.ok(challengeService.getChallenges(authUser.id(), status));
    }
}
