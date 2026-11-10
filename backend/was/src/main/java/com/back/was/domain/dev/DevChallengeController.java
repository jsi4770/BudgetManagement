package com.back.was.domain.dev;

import com.back.was.domain.dev.dto.CompleteChallengeRequest;
import com.back.was.domain.report.dto.GrowthReportResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명세 12장 시연용 API — <b>dev 프로필에서만 등록된다</b>(CLAUDE.md 규칙 ·
 * 서버_내부_처리 12장 "운영 환경에서는 {@code /dev/**} 비활성화").
 *
 * <p>{@code @Profile} 을 컨트롤러와 서비스 양쪽에 붙였다. 컨트롤러만 막으면 서비스 빈이
 * 운영 컨텍스트에 남아, 나중에 누군가 다른 곳에서 주입해 쓰는 길이 열린다.
 *
 * <p>인증은 필요하다. {@code SecurityConfig} 가 {@code /auth/*} 넷을 뺀 전부를
 * {@code authenticated()} 로 두므로 {@code /dev/**} 도 토큰이 있어야 하고, 대상은 항상
 * 토큰 주인의 챌린지다 — 시연 중에 남의 챌린지를 종료시킬 수는 없다.
 */
@RestController
@RequestMapping("/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevChallengeController {

    private final DevChallengeService devChallengeService;

    /**
     * DEV-02 챌린지 강제 종료. 응답은 <b>CHL-03 과 동일</b>하다(명세 DEV-02) — 버튼을 누른
     * 뒤 바로 성장 리포트 화면을 그릴 수 있다.
     *
     * <p>본문이 없어도 된다: {@code scenario} 를 보내지 않으면 "기간 종료 · 결과 보기",
     * 즉 현재 기록 그대로 판정한다(명세 DEV-02 표 첫 줄). COACH-01 과 같은 이유로
     * {@code required = false} + {@code emptyIfNull} 이다.
     *
     * <p>201 이 아니라 <b>200</b> 이다(명세 DEV-02). 거래와 리포트가 새로 생기지만 응답이
     * 가리키는 것은 "판정 결과"이고, 명세가 그렇게 정했다.
     */
    @PostMapping("/challenges/{id}/complete")
    public ApiResponse<GrowthReportResponse> complete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody(required = false) CompleteChallengeRequest request) {
        return ApiResponse.ok(devChallengeService.complete(
                authUser.id(), id, CompleteChallengeRequest.emptyIfNull(request)));
    }
}
