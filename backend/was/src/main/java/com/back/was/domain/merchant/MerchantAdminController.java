package com.back.was.domain.merchant;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code /admin/**} 는 SecurityConfig 에서 ADMIN 만 통과한다. */
@Tag(name = "11. 관리자 (ADMIN)", description = "관리자 전용 도구입니다.")
@Slf4j
@RestController
@RequestMapping("/admin/local-merchants")
@RequiredArgsConstructor
public class MerchantAdminController {

    private final MerchantSyncService syncService;
    private final MerchantFileImportService importService;

    @Operation(summary = "ADMIN 가맹점 API 동기화 시작 (기준일 2023-11, 보조용)", description = """
            공공데이터 API 에서 가맹점을 다시 받아 옵니다. 천안 약 1만 건·아산 약 3.8만 건이라 **몇 분** 걸리므로
            바로 `started: true` 로 응답하고 뒤에서 돕니다. 진행은 서버 로그로 봅니다.
            `regionCode` 를 생략하면 지원 지역 전체입니다.

            **실패**: `400 VALIDATION_ERROR` — 지원하지 않는 지역""")
    @PostMapping("/sync-api")
    public ApiResponse<Map<String, Object>> syncApi(@RequestParam(required = false) String regionCode) {
        var regions = regionCode == null || regionCode.isBlank()
                ? java.util.List.of(SupportedRegion.values())
                : java.util.List.of(SupportedRegion.fromCode(regionCode.strip())
                        .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 지역이에요.")));

        Thread.ofVirtual().name("merchant-admin-sync").start(() -> {
            for (SupportedRegion region : regions) {
                try {
                    syncService.sync(region);
                } catch (RuntimeException e) {
                    log.error("{} 가맹점 동기화 실패: {}", region.displayName(), e.getMessage());
                }
            }
        });
        return ApiResponse.ok(Map.of("started", true));
    }

    @Operation(summary = "ADMIN 가맹점 파일 적재", description = """
            서버에 포함된 지자체 공개 목록(아산 2026-10, 천안 2024-12 + 2026-07 등록취소 반영)을 DB 에 넣습니다.
            같은 가맹점은 갱신하고 파일에 없는 가맹점은 지웁니다. 몇 초~수십 초 걸려 바로 `started: true` 로 응답합니다.
            `regionCode` 를 생략하면 지원 지역 전체입니다.

            **실패**: `400 VALIDATION_ERROR` — 지원하지 않는 지역""")
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importFiles(@RequestParam(required = false) String regionCode) {
        var regions = regionCode == null || regionCode.isBlank()
                ? java.util.List.of(SupportedRegion.values())
                : java.util.List.of(SupportedRegion.fromCode(regionCode.strip())
                        .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 지역이에요.")));

        Thread.ofVirtual().name("merchant-admin-import").start(() -> {
            for (SupportedRegion region : regions) {
                try {
                    importService.importRegion(region);
                } catch (RuntimeException e) {
                    log.error("{} 가맹점 파일 적재 실패: {}", region.displayName(), e.getMessage());
                }
            }
        });
        return ApiResponse.ok(Map.of("started", true));
    }
}
