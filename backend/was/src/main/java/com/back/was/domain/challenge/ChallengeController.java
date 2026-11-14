package com.back.was.domain.challenge;

import com.back.was.domain.challenge.dto.ChallengeResponse;
import com.back.was.domain.report.dto.GrowthReportResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챌린지의 주인은 {@code COACHING.USER_ID} 를 거쳐 안다 — CHALLENGE 에
 * {@code USER_ID} 가 없다(ERD 9장).
 *
 * <p>목록은 {@code PageResponse} 를 쓰지 않는다 (명세 CHL-01 이 배열).
 */
@Tag(name = "11. 챌린지 & 성장 리포트 (CHL)", description = """
        확정된 절약 챌린지와 종료 후 리포트. **조회 전용**입니다 —
        만드는 것은 COACH-04, 실적 갱신은 거래 등록·수정·삭제가, 종료 판정은 매일 00:05 스케줄러가 합니다.

        수정·삭제·중도 포기 API 는 명세에 없습니다.""")
@RestController
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    /**
     * 검증 애너테이션이 없으므로 {@code @ModelAttribute} 레코드로 묶지 않았다 — 묶어야
     * 하는 건 제약이 붙은 쿼리 파라미터다(제약을 핸들러 파라미터에 직접 달면 Spring 7 의
     * 메서드 검증이 {@code HandlerMethodValidationException} 을 던져 500 이 된다).
     * TX-03 의 {@code yearMonth} 와 같은 판단이다.
     */
    @Operation(summary = "CHL-01 챌린지 목록", description = """
            `data` 가 바로 배열입니다(페이지 정보 없음). 최신 확정 순입니다.

            `status` 는 선택입니다(`IN_PROGRESS`/`SUCCESS`/`FAILED`, 없으면 전체).
            완료 목록은 전체를 받아 `SUCCESS`·`FAILED` 로 걸러 쓰세요.

            `daysLeft` 는 저장되지 않고 조회 시점에 계산합니다 — 종료일까지 남은 일수이고
            종료일 당일은 0, 이미 지났으면 0 입니다(음수가 되지 않습니다).

            **목표를 초과해도 자동 실패·취소되지 않습니다** — `achievementRate` 가 100 을 넘은
            `IN_PROGRESS` 로 표현되고, 판정은 기간이 끝난 다음 날에 한 번만 합니다.

            **실패**: `400 VALIDATION_ERROR` — `status` 가 세 값 중 하나가 아님""")
    @GetMapping
    public ApiResponse<List<ChallengeResponse>> getChallenges(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) ChallengeStatus status) {
        return ApiResponse.ok(challengeService.getChallenges(authUser.id(), status));
    }

    /**
     * 아래 {@code /{id}/report} 가 더 구체적이라 Spring 이 그쪽을 먼저 매칭한다 —
     * {@code report} 가 ID 로 해석되는 일은 없다.
     */
    @Operation(summary = "CHL-02 챌린지 상세", description = """
            CHL-01 항목과 **같은 형식**입니다(배열이 아닌 단일 객체). 종료된 챌린지도 열립니다.

            **실패**: `404 NOT_FOUND`""")
    @GetMapping("/{id}")
    public ApiResponse<ChallengeResponse> getChallenge(@AuthenticationPrincipal AuthUser authUser,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(challengeService.getChallenge(authUser.id(), id));
    }

    /** 404 세 가지(없음·남의 것·진행 중)를 합치는 이유는 {@code ChallengeService.getReport} 에 있다. */
    @Operation(summary = "CHL-03 성장 리포트", description = """
            챌린지가 **끝날 때 서버가 만듭니다** — 진행 중이면 없습니다.

            `before` 는 챌린지 시작 직전 같은 길이 기간의 같은 카테고리 소비이고(7일 챌린지면 직전 7일),
            판정 시점에 고정됩니다. `during` 은 챌린지 기간의 실적입니다.

            `difference` 는 `목표 − 실적` 이라 **양수면 목표보다 덜 쓴 것**입니다.

            **실패**: `404 NOT_FOUND` — 없는 챌린지·남의 챌린지·**아직 진행 중**이 모두 같은 404 입니다""")
    @GetMapping("/{id}/report")
    public ApiResponse<GrowthReportResponse> getReport(@AuthenticationPrincipal AuthUser authUser,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(challengeService.getReport(authUser.id(), id));
    }
}
