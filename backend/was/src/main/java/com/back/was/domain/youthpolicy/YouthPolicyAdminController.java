package com.back.was.domain.youthpolicy;

import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code /admin/**} 는 SecurityConfig 에서 ADMIN 만 통과한다. */
@Tag(name = "11. 관리자 (ADMIN)")
@Slf4j
@RestController
@RequestMapping("/admin/youth-policies")
@RequiredArgsConstructor
public class YouthPolicyAdminController {

    private final YouthPolicySyncService syncService;

    @Operation(summary = "ADMIN 청년정책 동기화 시작", description = """
            온통청년 API 에서 천안·아산 대상 정책을 다시 받아 옵니다. 바로 `started: true` 로 응답하고 뒤에서 돕니다.""")
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync() {
        Thread.ofVirtual().name("youth-policy-admin-sync").start(() -> {
            try {
                syncService.sync();
            } catch (RuntimeException e) {
                log.error("청년정책 동기화 실패: {}", e.getMessage());
            }
        });
        return ApiResponse.ok(Map.of("started", true));
    }
}
