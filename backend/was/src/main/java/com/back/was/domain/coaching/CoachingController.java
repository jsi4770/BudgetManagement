package com.back.was.domain.coaching;

import com.back.was.domain.coaching.dto.CoachingResponse;
import com.back.was.domain.coaching.dto.StartCoachingRequest;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
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
}
