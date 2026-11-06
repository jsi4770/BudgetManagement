package com.back.was.global.response;

import java.util.List;

/**
 * API 명세서 0장 3) 목록(페이지네이션) 응답의 {@code data} 본문.
 *
 * <p>{@code page}는 0-based (명세 예시 {@code "page": 0}).
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalCount,
        boolean hasNext
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalCount) {
        return new PageResponse<>(items, page, size, totalCount, (long) (page + 1) * size < totalCount);
    }
}
