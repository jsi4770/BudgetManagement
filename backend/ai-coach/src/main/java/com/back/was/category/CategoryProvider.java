package com.back.was.category;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI 쪽 코드가 카테고리 목록을 얻는 창구.
 * 프롬프트에 들어가는 목록, "기타" 대체, 월세 제외 판단이 전부 여기서 나온다.
 */
public interface CategoryProvider {

    /** 주간 분석·넛지에서 빼는 카테고리 이름 */
    Set<String> RENT_NAMES = Set.of("월세");
    /** 해당 없을 때 들어가는 카테고리 이름 (명세 TX-07) */
    String ETC_NAME = "기타";

    List<CategoryInfo> findAll();

    default Optional<CategoryInfo> findById(Long id) {
        if (id == null) return Optional.empty();
        return findAll().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    default Optional<CategoryInfo> findByName(String name) {
        return findAll().stream().filter(c -> c.name().equals(name)).findFirst();
    }

    default CategoryInfo etc() {
        return findByName(ETC_NAME)
                .orElseThrow(() -> new IllegalStateException("카테고리 목록에 '기타'가 없어요."));
    }

    default boolean isRent(Long id) {
        return findById(id).map(c -> RENT_NAMES.contains(c.name())).orElse(false);
    }

    default boolean isExpense(Long id) {
        return findById(id).map(CategoryInfo::isExpense).orElse(false);
    }
}
