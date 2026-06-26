package com.townbasket.catalog.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for {@code catalog.products}. Module-internal.
 *
 * <p>The {@code search_vector} tsvector column is maintained by a database
 * trigger, so it is intentionally not mapped here.
 */
@Entity
@Table(name = "products", schema = "catalog")
class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_kn")
    private String nameKn;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column
    private String description;

    @Column(name = "veg_marker", nullable = false)
    private boolean vegMarker;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private boolean available;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductVariantEntity> variants = new ArrayList<>();

    protected ProductEntity() {
        // JPA
    }

    Long getId() {
        return id;
    }

    Long getCategoryId() {
        return categoryId;
    }

    String getName() {
        return name;
    }

    String getNameKn() {
        return nameKn;
    }

    void setNameKn(String nameKn) {
        this.nameKn = nameKn;
    }

    String getSlug() {
        return slug;
    }

    String getDescription() {
        return description;
    }

    boolean isVegMarker() {
        return vegMarker;
    }

    String getImageUrl() {
        return imageUrl;
    }

    boolean isAvailable() {
        return available;
    }

    boolean isFeatured() {
        return featured;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    List<ProductVariantEntity> getVariants() {
        return variants;
    }
}
