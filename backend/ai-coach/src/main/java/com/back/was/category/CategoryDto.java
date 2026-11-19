package com.back.was.category;

/** 응답용 카테고리 {id, name, type} (TX-07, RCPT-01, TX-01 응답 모양) */
public record CategoryDto(Long id, String name, String type) {
    public static CategoryDto of(CategoryInfo c) {
        return new CategoryDto(c.id(), c.name(), c.type());
    }
}
