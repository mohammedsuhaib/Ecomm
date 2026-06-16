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

    Long getId() {
        return id;
    }

    Long getStoreId() {
        return storeId;
    }

    Long getVariantId() {
        return variantId;
    }

    int getOnHand() {
        return onHand;
    }

    int getReserved() {
        return reserved;
    }

    int getLowStockThreshold() {
        return lowStockThreshold;
    }

    int available() {
        return onHand - reserved;
    }
}
