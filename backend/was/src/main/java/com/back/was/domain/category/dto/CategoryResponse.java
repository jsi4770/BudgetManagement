package com.back.was.domain.category.dto;

import com.back.was.domain.category.Category;

/** CAT-01 응답 원소 — 명세 예시의 키는 {@code id} · {@code name} · {@code type} 뿐이다. */
public record CategoryResponse(
        Long id,
        String name,
        String type
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType().name());
    }
}
