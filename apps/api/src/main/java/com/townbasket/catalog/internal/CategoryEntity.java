package com.townbasket.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for {@code catalog.categories}. Module-internal: never leaves the
 * catalog module (mapped to {@code CategoryDto} before crossing the boundary).
 */
@Entity
@Table(name = "categories", schema = "catalog")
class CategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "image_url")
    private String imageUrl;

    protected CategoryEntity() {
        // JPA
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getSlug() {
        return slug;
    }

    int getSortOrder() {
        return sortOrder;
    }

    String getImageUrl() {
        return imageUrl;
    }
}
