package com.back.was.domain.category.dto;

import com.back.was.domain.category.Category;

/**
 * 카테고리를 <b>가리키기만</b> 하는 응답 조각 — {@code {id, name}}.
 *
 * <p>{@link CategoryResponse}({@code id} · {@code name} · {@code type})와 키가 달라서 따로
 * 둔다(명세의 키 구성이 다르면 타입도 다르다 — {@code MeResponse} vs
 * {@code UserResponse} 와 같은 판단). {@code type} 이 빠지는 곳은 그 값이 이미 자명한
 * 자리다: ANL-03 의 항목은 전부 지출이고, 넛지는 지출 카테고리에만 생긴다.
 *
 * <p>쓰는 곳: ANL-03 {@code items[].category}, NDG-01~03 {@code category}.
 */
public record CategoryRefResponse(
        Long id,
        String name
) {
    public static CategoryRefResponse from(Category category) {
        return new CategoryRefResponse(category.getId(), category.getName());
    }
}
