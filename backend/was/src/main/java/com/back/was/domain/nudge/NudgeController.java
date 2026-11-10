package com.back.was.domain.nudge;

import com.back.was.domain.nudge.dto.ChangeNudgeStatusRequest;
import com.back.was.domain.nudge.dto.NudgeDetailResponse;
import com.back.was.domain.nudge.dto.NudgeResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.response.NullableApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "9. 넛지 (NDG)", description = """
        "평소보다 많이 썼어요" 카드. **서버가 거래 저장 시점에 만들고** 프론트는 조회·상태 변경만 합니다.

        푸시·SSE 가 없으므로 홈 진입과 탭 재활성화 때 다시 조회하세요.""")
@RestController
@RequestMapping("/nudges")
@RequiredArgsConstructor
public class NudgeController {

    private final NudgeService nudgeService;

    /**
     * {@code data: null} 로 키를 남기려고 공통 {@link ApiResponse} 가 아니라
     * {@link NullableApiResponse} 를 쓴다 — 이유는 그 타입의 설명에 있다.
     *
     * <p>{@code /current} 는 리터럴이라 NDG-02 의 {@code /{id}} 보다 먼저 매칭된다 —
     * {@code TX-03 /transactions/calendar} 와 같은 구조다.
     */
    @Operation(summary = "NDG-01 홈 노출 넛지", description = """
            **카드가 없으면 `data: null` 이고 200 입니다** — 404 가 아닙니다. 대부분의 날은 카드가 없습니다.

            노출 조건 세 개: 이번 주(월요일 기준) · `UNREAD` 만 · 여러 개면 가장 최근에 갱신된 하나.
            그래서 지난 주 카드는 미확인이어도 뜨지 않습니다.

            `zScore` 는 내부 판정용이라 응답에 없습니다.""")
    @GetMapping("/current")
    public NullableApiResponse<NudgeResponse> getCurrentNudge(@AuthenticationPrincipal AuthUser authUser) {
        return NullableApiResponse.ok(nudgeService.getCurrentNudge(authUser.id()).orElse(null));
    }

    /**
     * NDG-01 과 달리 "없음"이 오류이므로 공통 {@link ApiResponse} 를 쓴다.
     *
     * <p>위의 {@code /current} 보다 덜 구체적이므로 Spring 이 리터럴 경로를 먼저 고른다.
     */
    @Operation(summary = "NDG-02 넛지 상세", description = """
            NDG-01 의 필드 + `aiAnalysis`·`aiRecommendation` 두 개입니다.

            **NDG-01 의 노출 조건이 걸리지 않습니다** — 지난 주 카드도, 이미 닫은 카드도 열립니다.

            AI 문구는 커밋 후 비동기로 채워지므로 생성 직후에는 `null` 일 수 있습니다.
            그게 "AI가 분석 중이에요" 신호이니 2~3회 재조회하세요.

            **실패**: `404 NOT_FOUND` — 없는 넛지와 남의 넛지가 같은 404 입니다""")
    @GetMapping("/{id}")
    public ApiResponse<NudgeDetailResponse> getNudge(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long id) {
        return ApiResponse.ok(nudgeService.getNudge(authUser.id(), id));
    }

    @Operation(summary = "NDG-03 넛지 상태 변경", description = """
            응답은 NDG-02 와 같은 형식입니다.

            보낼 수 있는 값은 `DISMISSED`(닫음) · `VIEWED`(소비 현황 확인) · `COACHING_STARTED` 셋입니다.
            **`UNREAD` 로 되돌릴 수 없습니다** — 닫은 카드가 다시 뜨지 않게 하는 규칙입니다.

            같은 상태를 다시 보내는 것은 200 입니다(재시도 허용). 넛지가 다시 판정돼 금액이
            갱신돼도 이 상태는 유지됩니다.

            **실패**: `400 VALIDATION_ERROR`(세 값 외), `404 NOT_FOUND`""")
    @PatchMapping("/{id}")
    public ApiResponse<NudgeDetailResponse> changeStatus(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long id,
                                                         @RequestBody @Valid ChangeNudgeStatusRequest request) {
        return ApiResponse.ok(nudgeService.changeStatus(authUser.id(), id, request));
    }
}
