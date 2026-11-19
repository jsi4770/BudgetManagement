package com.back.was.category;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TODO(연결 필요): DB 카테고리 테이블에서 읽도록 바꾸세요. id 가 DB 와 다르면 저장 시 엉뚱한 카테고리로 들어갑니다.
 *
 * 예) JPA
 *   private final CategoryRepository categoryRepository;
 *   public List<CategoryInfo> findAll() {
 *       return categoryRepository.findAll().stream()
 *               .map(c -> new CategoryInfo(c.getId(), c.getName(), c.getType().name()))
 *               .toList();
 *   }
 *
 * 지금은 팀에서 정한 6개 + 기타를 임시로 하드코딩해 두었습니다.
 */
@Component
public class CategoryProviderImpl implements CategoryProvider {

    private static final List<CategoryInfo> DEFAULTS = List.of(
            new CategoryInfo(1L, "식비", "EXPENSE"),
            new CategoryInfo(2L, "배달", "EXPENSE"),
            new CategoryInfo(3L, "카페", "EXPENSE"),
            new CategoryInfo(4L, "교통", "EXPENSE"),
            new CategoryInfo(5L, "월세", "EXPENSE"),
            new CategoryInfo(6L, "급여", "INCOME"),
            new CategoryInfo(7L, "기타", "EXPENSE")   // 6개 어디에도 안 맞을 때만
    );

    @Override
    public List<CategoryInfo> findAll() {
        return DEFAULTS;
    }
}
