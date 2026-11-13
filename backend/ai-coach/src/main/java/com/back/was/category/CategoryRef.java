package com.back.was.category;

/** 응답용 카테고리 {id, name} (ANL-03, NDG 응답 모양) */
public record CategoryRef(Long id, String name) {
    public static CategoryRef of(CategoryInfo c) {
        return new CategoryRef(c.id(), c.name());
    }
}
