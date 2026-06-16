package com.townbasket.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code inventory.reservations}. Module-internal. One row per
 * reserved order line; {@code status} moves RESERVED -> COMMITTED | RELEASED.
 */
@Entity
@Table(name = "reservations", schema = "inventory")
class ReservationEntity {

    static final String RESERVED = "RESERVED";
    static final String COMMITTED = "COMMITTED";
    static final String RELEASED = "RELEASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ReservationEntity() {
        // JPA
    }

    ReservationEntity(Long orderId, Long variantId, int qty) {
        this.orderId = orderId;
        this.variantId = variantId;
        this.qty = qty;
        this.status = RESERVED;
    }

    Long getId() {
        return id;
    }

    Long getOrderId() {
        return orderId;
    }

    Long getVariantId() {
        return variantId;
    }

    int getQty() {
        return qty;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }
}
