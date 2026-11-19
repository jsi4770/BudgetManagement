package com.back.was.domain.dev;

import com.back.was.domain.dev.dto.CompleteChallengeRequest;
import com.back.was.domain.report.dto.GrowthReportResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>{@code @Profile} 을 컨트롤러와 서비스 양쪽에 붙였다.</b> 컨트롤러만 막으면 서비스 빈이
 * 운영 컨텍스트에 남아, 나중에 누군가 다른 곳에서 주입해 쓰는 길이 열린다.
 */
@Tag(name = "12. 시연용 (DEV)", description = """
        시연 중에 상태를 즉시 만들기 위한 도구입니다. **dev 프로필에서만 등록**되므로
        운영 문서에는 나타나지 않습니다. 대상은 항상 토큰 주인의 데이터입니다.""")
@RestController
@RequestMapping("/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevChallengeController {

    private final DevChallengeService devChallengeService;

    /**
     * 본문이 없어도 되므로 COACH-01 과 같은 이유로 {@code required = false} +
     * {@code emptyIfNull} 이다 — 기본값({@code required = true})이면 빈 요청이 400 이 된다.
     */
    @Operation(summary = "DEV-02 챌린지 강제 종료", description = """
            종료일을 기다리지 않고 판정을 즉시 실행합니다. 응답은 **CHL-03 과 동일**해서
            버튼을 누른 뒤 바로 성장 리포트 화면을 그릴 수 있습니다.

            - `scenario` 없음: 현재 기록 그대로 판정 ("기간 종료 · 결과 보기")
            - `SUCCESS`: 오늘 날짜로 거래를 보태 목표의 70% 로 맞춘 뒤 판정
            - `FAILED`: 목표의 112% 가 되도록 보탠 뒤 판정

            보태는 거래는 TX-01 과 같은 경로로 들어가므로 챌린지 실적이 즉시 갱신됩니다.
            **두 번 눌러도 첫 판정이 유지됩니다** — 판정은 되돌릴 수 없습니다.

            거래 추가는 지출을 늘릴 수만 있어서, 이미 목표를 넘긴 상태에서 `SUCCESS` 를 눌러도
            되돌릴 수 없습니다(실제 기록대로 판정됩니다).

            **실패**: `400 VALIDATION_ERROR`(`scenario` 오타), `404 NOT_FOUND`""")
    @PostMapping("/challenges/{id}/complete")
    public ApiResponse<GrowthReportResponse> complete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody(required = false) CompleteChallengeRequest request) {
        return ApiResponse.ok(devChallengeService.complete(
                authUser.id(), id, CompleteChallengeRequest.emptyIfNull(request)));
    }
}
