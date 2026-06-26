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

    /**
     * Creation factory for a new product (admin write path). {@code created_at} is
     * DB-defaulted (not insertable) and {@code search_vector} is trigger-maintained,
     * so neither is set here.
     */
    static ProductEntity create(Long categoryId, String name, String nameKn, String slug,
                                String description, boolean vegMarker, String imageUrl,
                                boolean available, boolean featured) {
        ProductEntity p = new ProductEntity();
        p.categoryId = categoryId;
        p.name = name;
        p.nameKn = nameKn;
        p.slug = slug;
        p.description = description;
        p.vegMarker = vegMarker;
        p.imageUrl = imageUrl;
        p.available = available;
        p.featured = featured;
        return p;
    }

    Long getId() {
        return id;
    }

    Long getCategoryId() {
        return categoryId;
    }

    void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
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

    void setDescription(String description) {
        this.description = description;
    }

    boolean isVegMarker() {
        return vegMarker;
    }

    void setVegMarker(boolean vegMarker) {
        this.vegMarker = vegMarker;
    }

    String getImageUrl() {
        return imageUrl;
    }

    void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    boolean isAvailable() {
        return available;
    }

    void setAvailable(boolean available) {
        this.available = available;
    }

    boolean isFeatured() {
        return featured;
    }

    void setFeatured(boolean featured) {
        this.featured = featured;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    List<ProductVariantEntity> getVariants() {
        return variants;
    }
}
