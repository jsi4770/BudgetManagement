package com.back.was.global.exception;

import com.back.was.global.response.ApiResponse;
import com.back.was.global.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 명세 0장 3) 응답 형식과 4) 에러 코드가 실제 HTTP 응답으로 나오는지 확인한다.
 *
 * <p>{@code @WebMvcTest}는 JPA 자동 구성을 로드하지 않으므로 datasource 없이 돈다
 * (레포지토리 전체 테스트는 datasource가 설정되기 전까지 돌 수 없다).
 *
 * <p>{@code addFilters = false}로 security 필터 체인을 끈다. 401/403 경로는 이
 * advice가 아니라 EntryPoint·AccessDeniedHandler가 담당하므로, 아직
 * {@code SecurityFilterChain} 빈이 없는 상태에서는 여기서 검증할 수 없다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("성공 응답은 success·data만 담고 error 키는 없다")
    void successShape() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("목록 응답은 data 안에 items·page·size·totalCount·hasNext를 담는다")
    void pageShape() throws Exception {
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(57))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태·코드로 나가고 data 키는 없다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/duplicated"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("@Valid 실패는 400 VALIDATION_ERROR + details[].field/reason")
    void validationErrorWithDetails() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'amount')].reason").value("REQUIRED"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'memo')].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락은 reason REQUIRED로 나간다")
    void missingParam() throws Exception {
        mockMvc.perform(get("/test/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("yearMonth"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
    }

    @Test
    @DisplayName("깨진 JSON은 VALIDATION_ERROR이지만 details는 내보내지 않는다")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    @DisplayName("예상치 못한 예외는 500 INTERNAL_ERROR로 모이고 원인 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    @Test
    @DisplayName("소유권 위반은 403이 아니라 404 NOT_FOUND로 내린다 (명세 0장 5)")
    void ownershipViolationIsNotFound() throws Exception {
        mockMvc.perform(get("/test/others-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @RestController
    static class TestController {

        record Dto(long id) {
        }

        record ValidateRequest(@NotNull Integer amount, @NotBlank String memo) {
        }

        @GetMapping("/test/ok")
        ApiResponse<Dto> ok() {
            return ApiResponse.ok(new Dto(1));
        }

        @GetMapping("/test/page")
        ApiResponse<PageResponse<Dto>> page() {
            return ApiResponse.ok(PageResponse.of(List.of(new Dto(1)), 0, 20, 57));
        }

        @GetMapping("/test/duplicated")
        ApiResponse<Void> duplicated() {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        @PostMapping("/test/validate")
        ApiResponse<Void> validate(@RequestBody @Valid ValidateRequest request) {
            return ApiResponse.ok(null);
        }

        @GetMapping("/test/search")
        ApiResponse<Void> search(@RequestParam String yearMonth) {
            return ApiResponse.ok(null);
        }

        @GetMapping("/test/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("DB 커넥션 풀 고갈 — 응답에 새면 안 된다");
        }

        @GetMapping("/test/others-resource")
        ApiResponse<Void> othersResource() {
            throw BusinessException.notFound();
        }
    }
}
