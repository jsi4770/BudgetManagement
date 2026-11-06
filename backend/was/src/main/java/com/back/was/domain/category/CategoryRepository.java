package com.back.was.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * ID 순서는 ERD 4장 시드 표의 순서이자 화면의 표시 순서다(식비 → 배달 → … → 기타).
     * 이름 사전순으로 정렬하면 "기타"가 중간에 끼므로 ID 순을 쓴다.
     */
    List<Category> findAllByOrderByIdAsc();

    List<Category> findAllByTypeOrderByIdAsc(CategoryType type);

    /** {@link CategorySeeder} 의 멱등성 — 복합 UNIQUE {@code (NAME, TYPE)} 기준. */
    boolean existsByNameAndType(String name, CategoryType type);
}
