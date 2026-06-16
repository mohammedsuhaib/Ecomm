package com.townbasket.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code inventory.stock_movements} — append-only audit ledger.
 * Module-internal.
 */
@Entity
@Table(name = "stock_movements", schema = "inventory")
class StockMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected StockMovementEntity() {
        // JPA
    }

    StockMovementEntity(Long variantId, int delta, String reason) {
        this.variantId = variantId;
        this.delta = delta;
        this.reason = reason;
    }

    Long getId() {
        return id;
    }
}
