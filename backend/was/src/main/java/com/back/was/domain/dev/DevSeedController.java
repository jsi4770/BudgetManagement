package com.back.was.domain.dev;

import com.back.was.domain.dev.dto.SeedRequest;
import com.back.was.domain.dev.dto.SeedResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code DevChallengeController}(DEV-02) 와 같은 {@code /dev} 아래지만 클래스를 나눴다 —
 * 하는 일이 전혀 다르고(데이터 전체 교체 vs 챌린지 한 건 종료) 의존성도 겹치지 않는다.
 * {@code @Profile} 은 서비스에도 붙어 있다.
 */
@Tag(name = "12. 시연용 (DEV)", description = """
        시연 중에 상태를 즉시 만들기 위한 도구입니다. **dev 프로필에서만 등록**되므로
        운영 문서에는 나타나지 않습니다. 대상은 항상 토큰 주인의 데이터입니다.""")
@RestController
@RequestMapping("/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedController {

    private final DevSeedService devSeedService;

    /**
     * {@code scenario} 가 필수라 {@code @Valid} 가 붙는다 — DEV-02 와 다른 점이고 이유는
     * {@link SeedRequest} 에 있다.
     */
    @Operation(summary = "DEV-01 시연 데이터 초기화", description = """
            **내 거래·예산·넛지·코칭·챌린지·리포트를 전부 지우고** 시나리오 데이터를 다시 깝니다.
            가입일도 D−150일로 밀어 둡니다(그래야 넛지의 직전 4주 비교와 월간 탭의 3개월 비교가 모두 동작).

            - `NORMAL`: 직전 3개월 + 이번 달 평소 수준 → **넛지 없음**
            - `DELIVERY_SPIKE`: 위와 같은 과거 + 이번 주 배달 급증 → **넛지 생성**

            둘의 차이는 이번 주 배달 거래뿐이라 같은 화면에서 번갈아 보여 줄 수 있습니다.
            완료된 챌린지 4건(3건 성공)도 함께 들어가고, **진행 중 챌린지는 넣지 않습니다** —
            시연에서 COACH-04 로 직접 만들어야 하니까요.

            응답의 `nudgeCreated` 로 홈에 들어가기 전에 넛지가 떴는지 확인할 수 있습니다.

            **실패**: `400 VALIDATION_ERROR` — `scenario` 누락·오타""")
    @PostMapping("/seed")
    public ApiResponse<SeedResponse> seed(@AuthenticationPrincipal AuthUser authUser,
                                          @RequestBody @Valid SeedRequest request) {
        return ApiResponse.ok(devSeedService.seed(authUser.id(), request));
    }
}
