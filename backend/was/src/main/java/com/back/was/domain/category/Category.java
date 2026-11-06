package com.back.was.domain.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ERD 4장 CATEGORY.
 *
 * <p>사용자가 만들거나 지우는 리소스가 아니다. 목록은 ERD 4장의 시드 9개로 고정이고
 * ({@link CategorySeeder}) 명세에도 조회(CAT-01)만 있다. 그래서 생성 API 가 없고,
 * 엔티티에도 수정 메서드를 두지 않는다.
 */
@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categories_name_type",
                columnNames = {"name", "type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Category(String name, CategoryType type) {
        this.name = name;
        this.type = type;
    }

    /** 시드 입력 전용 — 런타임에 카테고리를 추가하는 경로는 없다. */
    static Category of(String name, CategoryType type) {
        return new Category(name, type);
    }
}
