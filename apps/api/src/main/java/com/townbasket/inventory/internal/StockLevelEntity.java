package com.townbasket.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for {@code inventory.stock_levels}. Module-internal.
 *
 * <p>Available stock is {@code on_hand - reserved}. Reservation is performed by
 * a single conditional UPDATE (see {@code StockLevelRepository.reserve}) so the
 * oversell race is impossible.
 */
@Entity
@Table(name = "stock_levels", schema = "inventory")
class StockLevelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    protected StockLevelEntity() {
        // JPA
    }

    /** A fresh zero-stock row for a newly created variant (threshold matches the schema default). */
    static StockLevelEntity zeroRow(Long storeId, Long variantId) {
        StockLevelEntity e = new StockLevelEntity();
        e.storeId = storeId;
        e.variantId = variantId;
        e.onHand = 0;
        e.reserved = 0;
        e.lowStockThreshold = 5;
        return e;
    }

    Long getId() {
        return id;
    }

    Long getVariantId() {
        return variantId;
    }

    int getOnHand() {
        return onHand;
    }

    void setOnHand(int onHand) {
        this.onHand = onHand;
    }

    int getReserved() {
        return reserved;
    }

    int available() {
        return onHand - reserved;
    }
}
