package com.back.was.domain.merchant;

import com.back.was.domain.merchant.dto.MerchantResponse;
import com.back.was.domain.merchant.dto.RegionResponse;
import com.back.was.global.response.ApiResponse;
import com.back.was.global.response.PageResponse;
import com.back.was.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "13. 지역 가맹점 (MERCHANT)", description = """
        내 지역(천안·아산)의 지역사랑상품권 가맹점. 한국조폐공사 통합 가맹점기본정보를 서버가 미리
        적재해 둔 것이라 응답이 빠릅니다. **영업 중인 가맹점만** 있고, 기준일은 각 항목의 `baseDate` 입니다.""")
@RestController
@RequestMapping("/local-merchants")
@RequiredArgsConstructor
public class LocalMerchantController {

    private final LocalMerchantService merchantService;

    @Operation(summary = "MERCHANT-01 지원 지역 목록", description = """
            지역을 고를 수 있는 시군입니다. 선택한 코드는 `PATCH /users/me` 의 `regionCode` 로 저장합니다.""")
    @GetMapping("/regions")
    public ApiResponse<List<RegionResponse>> regions() {
        return ApiResponse.ok(Arrays.stream(SupportedRegion.values()).map(RegionResponse::from).toList());
    }

    @Operation(summary = "MERCHANT-02 내 지역 가맹점 목록", description = """
            기본은 **내가 저장한 지역**입니다. `regionCode` 를 주면 그 지역을 대신 봅니다.
            상호순이며 `page` 는 0부터, `size` 는 1~50(기본 20)입니다.

            `group`: `FOOD`(식당·주점) `CAFE` `GROCERY`(마트·편의점) `SHOPPING` `TRANSPORT`(교통·주유)
            `HEALTH` `EDU` `LEISURE` `LIFE` `ETC`. `q` 는 상호 부분 검색입니다.

            **실패**: `400 VALIDATION_ERROR` — 지원하지 않는 지역 / 지역을 저장하지 않았고 `regionCode` 도 없음 /
            `group` 이 목록에 없는 값""")
    @GetMapping
    public ApiResponse<PageResponse<MerchantResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) MerchantGroup group,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(merchantService.search(authUser.id(), regionCode, group, q, page, size));
    }
}
