package com.back.was.domain.admin;

import com.back.was.domain.admin.dto.KpiResponse;
import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이 프로젝트에서 <b>유일하게 {@code FORBIDDEN} 이 나는 경로</b>다. 일반 사용자
 * ({@code role: USER})가 부르면 403 이고, 그 판단은 여기가 아니라
 * {@code SecurityConfig} 의 {@code /admin/**} → {@code hasRole("ADMIN")} 이 한다 — 다른
 * 도메인의 "남의 리소스"는 404 이고(명세 0장 5)) 403 은 관리자 권한 전용이라는 규칙의
 * 반대쪽 끝이다.
 *
 * <p>{@code @AuthenticationPrincipal} 이 없다. 집계가 프로젝트 전체라서 누가 불렀는지는
 * 결과에 영향을 주지 않는다 — 토큰이 ADMIN 인지만 중요하고 그건 필터 체인이 이미 봤다.
 */
@Tag(name = "13. 관리자 (ADMIN)", description = """
        발표용 지표. **`role: ADMIN` 토큰만 허용**하고 일반 사용자는 `403 FORBIDDEN` 입니다 —
        이 프로젝트에서 403 이 나는 유일한 곳입니다(다른 도메인의 "남의 리소스"는 404).

        관리자 계정은 시드로 만들어지고 dev 프로필 전용입니다.

        > ⚠️ **아직 구현되지 않았습니다** — 지금은 모든 엔드포인트가 `500 INTERNAL_ERROR` 를 반환합니다.""")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminKpiController {

    private final AdminKpiService adminKpiService;

    @Operation(summary = "ADMIN-01 KPI 조회", description = """
            종료된 챌린지(`SUCCESS`+`FAILED`) 중 달성 비율입니다. 진행 중인 것은 분모에 넣지 않습니다.

            **집계는 프로젝트 전체**입니다 — 사용자별이 아닙니다. 목표(`target`)는 70.00% 이고
            서버가 함께 내려줍니다(화면에 박아 두면 발표 자료와 어긋날 수 있어서).

            종료된 챌린지가 하나도 없으면 `0건 / 0.00% / isAchieved: false` 입니다.

            **실패**: `403 FORBIDDEN` — 일반 사용자, `401 UNAUTHORIZED` — 토큰 없음""")
    @GetMapping("/kpi")
    public ApiResponse<KpiResponse> getKpi() {
        return ApiResponse.ok(adminKpiService.getKpi());
    }
}
