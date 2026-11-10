package com.back.was.domain.category;

import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 목록이 9건 고정이라 {@code PageResponse} 를 쓰지 않는다 (명세 CAT-01 예시). */
@Tag(name = "4. 카테고리 (CAT)", description = """
        고정 9개(지출 6 · 수입 3). 서버 시드라 추가·수정 API 가 없습니다.""")
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "CAT-01 카테고리 목록", description = """
            `data` 가 바로 배열입니다 — 9개 고정이라 페이지 정보가 없습니다.

            `type` 은 선택입니다(`INCOME`/`EXPENSE`, 없으면 전체). ID 순으로 내려갑니다.

            **실패**: `400 VALIDATION_ERROR` — `type` 이 두 값 중 하나가 아님""")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(
            @RequestParam(required = false) CategoryType type) {
        return ApiResponse.ok(categoryService.getCategories(type));
    }
}
