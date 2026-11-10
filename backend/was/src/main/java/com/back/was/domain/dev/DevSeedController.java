package com.back.was.domain.dev;

import com.back.was.domain.dev.dto.SeedRequest;
import com.back.was.domain.dev.dto.SeedResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV-01 시연 데이터 초기화 — <b>dev 프로필 전용</b>(CLAUDE.md 규칙 · 서버_내부_처리 12장).
 *
 * <p>{@code DevChallengeController}(DEV-02) 와 같은 {@code /dev} 아래지만 클래스를 나눴다.
 * 하는 일이 전혀 다르고(데이터 전체 교체 vs 챌린지 한 건 종료) 의존성도 겹치지 않는다.
 *
 * <p>대상은 <b>토큰 주인</b>의 데이터다. 명세 DEV-01 의 "내 거래·예산·넛지·코칭·챌린지
 * 전체 삭제"가 그 뜻이고, 그래서 사용자 ID 를 받는 파라미터가 없다.
 */
@RestController
@RequestMapping("/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedController {

    private final DevSeedService devSeedService;

    /**
     * DEV-01. 마이페이지의 "정상 소비 상황으로 초기화" / "배달비 증가 상황으로 초기화"
     * 두 버튼이 각각 {@code NORMAL}·{@code DELIVERY_SPIKE} 를 보낸다.
     *
     * <p>{@code scenario} 는 필수라 {@code @Valid} 가 붙는다 — DEV-02 와 다른 점이고
     * 이유는 {@link SeedRequest} 에 있다.
     *
     * <p>201 이 아니라 <b>200</b> 이다(명세 DEV-01). 수백 건이 생기지만 응답이 가리키는
     * 것은 새 리소스가 아니라 "초기화 결과"다.
     */
    @PostMapping("/seed")
    public ApiResponse<SeedResponse> seed(@AuthenticationPrincipal AuthUser authUser,
                                          @RequestBody @Valid SeedRequest request) {
        return ApiResponse.ok(devSeedService.seed(authUser.id(), request));
    }
}
