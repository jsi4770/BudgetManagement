package com.back.was.domain.category;

import com.back.was.domain.category.dto.CategoryResponse;
import com.back.was.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 명세 5장 카테고리(CATEGORY).
 *
 * <p>목록이 9건으로 고정이라 페이지네이션 없이 {@code data} 가 바로 배열이다
 * (명세 CAT-01 예시). 그래서 {@code PageResponse} 를 쓰지 않는다.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * CAT-01 카테고리 목록.
     *
     * <p>{@code type} 은 선택이고, {@code INCOME}/{@code EXPENSE} 가 아닌 값은 Spring 의
     * enum 변환이 실패해 {@code MethodArgumentTypeMismatchException} → 400
     * {@code VALIDATION_ERROR} 로 떨어진다(명세 0장 2): ENUM 은 대문자 영문).
     */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(
            @RequestParam(required = false) CategoryType type) {
        return ApiResponse.ok(categoryService.getCategories(type));
    }
}
