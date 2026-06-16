package com.townbasket.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity for {@code catalog.product_variants}. Module-internal.
 *
 * <p><strong>cost_price is internal-only.</strong> It is read here for
 * gross-profit reporting but is never copied into any DTO returned by the API.
 */
@Entity
@Table(name = "product_variants", schema = "catalog")
class ProductVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Managed by the owning ProductEntity @OneToMany @JoinColumn; read-only here.
    @Column(name = "product_id", insertable = false, updatable = false)
    private Long productId;

    @Column(nullable = false)
    private String label;

    @Column(name = "selling_price", nullable = false)
    private BigDecimal sellingPrice;

    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    @Column
    private BigDecimal mrp;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductVariantEntity() {
        // JPA
    }

    Long getId() {
        return id;
    }

    Long getProductId() {
        return productId;
    }

    String getLabel() {
        return label;
    }

    BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    /** Internal-only — must not be exposed in any API response. */
    BigDecimal getCostPrice() {
        return costPrice;
    }

    BigDecimal getMrp() {
        return mrp;
    }

    boolean isAvailable() {
        return available;
    }

    int getSortOrder() {
        return sortOrder;
    }
}
