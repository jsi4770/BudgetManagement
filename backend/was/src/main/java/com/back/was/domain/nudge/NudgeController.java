package com.back.was.domain.nudge;

import com.back.was.domain.nudge.dto.NudgeDetailResponse;
import com.back.was.domain.nudge.dto.NudgeResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.response.NullableApiResponse;
import com.back.was.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 명세 9장 넛지(NUDGE). 대상은 항상 토큰의 주인이라 경로에 사용자 ID 가 없다. */
@RestController
@RequestMapping("/nudges")
@RequiredArgsConstructor
public class NudgeController {

    private final NudgeService nudgeService;

    /**
     * NDG-01 홈 노출 넛지 1개.
     *
     * <p>넛지가 없으면 {@code {"success": true, "data": null}} 이다 — 404 가 아니다. 홈
     * 화면에 카드가 없는 건 오류가 아니고(대부분의 날이 그렇다), 404 로 내리면 프론트가
     * "찾을 수 없음" 처리와 섞인다. 키를 남기려고
     * {@link NullableApiResponse} 를 쓰는 이유는 그 타입의 설명에 있다.
     *
     * <p>{@code /current} 는 리터럴이라 NDG-02 의 {@code /{id}} 보다 먼저 매칭된다 —
     * {@code TX-03 /transactions/calendar} 와 같은 구조다. 누군가 이 경로를 경로 변수로
     * 바꾸지 않는 한 {@code current} 가 ID 로 해석되는 일은 없다.
     */
    @GetMapping("/current")
    public NullableApiResponse<NudgeResponse> getCurrentNudge(@AuthenticationPrincipal AuthUser authUser) {
        return NullableApiResponse.ok(nudgeService.getCurrentNudge(authUser.id()).orElse(null));
    }

    /**
     * NDG-02 넛지 상세. NDG-01 과 달리 <b>없으면 404</b> 다 — 홈 카드가 없는 건 정상이지만,
     * ID 로 지목한 넛지가 없는 건 "찾을 수 없음"이다. 그래서 공통
     * {@link ApiResponse} 를 쓴다.
     *
     * <p>이 매핑은 위의 {@code /current} 보다 덜 구체적이므로 Spring 이 리터럴 경로를 먼저
     * 고른다 — {@code current} 가 ID 로 들어오는 일은 없다.
     */
    @GetMapping("/{id}")
    public ApiResponse<NudgeDetailResponse> getNudge(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(nudgeService.getNudge(authUser.id(), id));
    }
}
