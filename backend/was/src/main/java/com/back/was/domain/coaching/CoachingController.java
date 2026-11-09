package com.back.was.domain.coaching;

import com.back.was.domain.coaching.dto.CoachingResponse;
import com.back.was.domain.coaching.dto.ConfirmGoalRequest;
import com.back.was.domain.coaching.dto.ConfirmGoalResponse;
import com.back.was.domain.coaching.dto.SendMessageRequest;
import com.back.was.domain.coaching.dto.SendMessageResponse;
import com.back.was.domain.coaching.dto.StartCoachingRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 명세 10장 AI 코칭(COACHING). 세션은 항상 토큰 주인의 것이라 경로에 사용자 ID 가 없다. */
@RestController
@RequestMapping("/coachings")
@RequiredArgsConstructor
public class CoachingController {

    private final CoachingService coachingService;

    /**
     * COACH-01 코칭 시작.
     *
     * <p><b>본문이 없어도 된다</b>({@code required = false}). 유일한 필드인
     * {@code nudgeId} 가 선택이라 "넛지 없이 시작"은 보낼 값이 없는 요청이고, 그때
     * {@code @RequestBody} 기본값({@code required = true})은
     * {@code HttpMessageNotReadableException} → 400 을 만든다. 명세 COACH-01 에 없는
     * 에러이므로 {@link StartCoachingRequest#emptyIfNull} 로 빈 요청과 같게 다룬다.
     *
     * <p>검증 애너테이션이 없으므로 {@code @Valid} 도 없다 — {@code nudgeId} 의 유효성은
     * DB 를 봐야 알 수 있고, 서비스가 404 로 답한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachingResponse> start(@AuthenticationPrincipal AuthUser authUser,
                                               @RequestBody(required = false) StartCoachingRequest request) {
        return ApiResponse.ok(
                coachingService.start(authUser.id(), StartCoachingRequest.emptyIfNull(request)));
    }

    /**
     * COACH-02 코칭 조회 (재진입). 응답 타입이 COACH-01 과 같은
     * {@link CoachingResponse} 인 것은 명세 COACH-02 가 "COACH-01과 같은 형식"을 요구한
     * 결과다 — 프론트가 두 응답을 같은 코드로 그린다.
     *
     * <p>상태 코드만 다르다: 시작은 201, 조회는 200.
     */
    @GetMapping("/{id}")
    public ApiResponse<CoachingResponse> getCoaching(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(coachingService.getCoaching(authUser.id(), id));
    }

    /**
     * COACH-03 메시지 전송 → AI 응답.
     *
     * <p>201 이 아니라 <b>200</b> 이다(명세 COACH-03). 메시지 두 건이 새로 생기지만 응답이
     * 가리키는 것은 새 리소스가 아니라 "이번 턴의 결과"이고, 명세가 그렇게 정했다.
     *
     * <p>AI 가 죽어도 200 이다 — 대체 문구와 {@code isFallback: true} 가 내려간다.
     * 자세한 이유는 {@code CoachingService.sendMessage}.
     */
    @PostMapping("/{id}/messages")
    public ApiResponse<SendMessageResponse> sendMessage(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @RequestBody @Valid SendMessageRequest request) {
        return ApiResponse.ok(coachingService.sendMessage(authUser.id(), id, request));
    }

    /**
     * COACH-04 목표 확정 → 챌린지 생성.
     *
     * <p><b>201</b> 이다(명세 COACH-04) — COACH-03 과 달리 새 리소스({@code CHALLENGE})가
     * 실제로 만들어지고 응답이 그 ID 를 가리킨다.
     *
     * <p>{@code /confirm} 과 {@code /messages} 는 둘 다 리터럴이라 서로, 그리고 COACH-02 의
     * {@code /{id}} 와도 겹치지 않는다 (메서드도 GET/POST 로 다르다).
     */
    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfirmGoalResponse> confirmGoal(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @RequestBody @Valid ConfirmGoalRequest request) {
        return ApiResponse.ok(coachingService.confirmGoal(authUser.id(), id, request));
    }
}
