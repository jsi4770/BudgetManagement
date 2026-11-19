package com.back.was.domain.youthpolicy;

import com.back.was.domain.youthpolicy.dto.YouthPolicyResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "14. 청년정책 (YOUTH-POLICY)", description = """
        온통청년 청년정책 중 내 지역(천안·아산)이 대상인 것을 서버가 미리 적재해 둔 사본입니다.""")
@RestController
@RequestMapping("/youth-policies")
@RequiredArgsConstructor
public class YouthPolicyController {

    private final YouthPolicyService service;

    @Operation(summary = "YOUTH-01 홈 상단 롤링용 맞춤 정책", description = """
            내 **지역·나이**에 맞고 **지금 신청 가능한** 정책을 카드 순서대로 돌려줍니다. `limit` 기본 10, 최대 20.
            순서: 지역 맞춤 정책 → 마감 임박 → 상시. `dDay` 는 마감까지 남은 일수(상시는 `null`).
            나이를 아직 모르면 나이는 거르지 않습니다. 결과가 비어 있을 수 있으니 빈 배열이면 롤링 영역을 숨기세요.

            **실패**: `400 VALIDATION_ERROR` — 지역을 아직 설정하지 않음""")
    @GetMapping("/home")
    public ApiResponse<List<YouthPolicyResponse>> home(@AuthenticationPrincipal AuthUser authUser,
                                                       @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.home(authUser.id(), limit));
    }
}
