package com.back.was.domain.category;

import com.back.was.domain.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CAT-01 카테고리 목록. */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /** @param type {@code null} 이면 명세 CAT-01 대로 수입·지출 전체를 준다. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(CategoryType type) {
        List<Category> categories = type == null
                ? categoryRepository.findAllByOrderByIdAsc()
                : categoryRepository.findAllByTypeOrderByIdAsc(type);

        return categories.stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
