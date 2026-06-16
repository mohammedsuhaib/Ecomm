package com.townbasket.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code orders.order_events} — the per-order audit trail /
 * timeline. Module-internal. The owning {@code @ManyToOne} sets {@code order_id}
 * on insert.
 */
@Entity
@Table(name = "order_events", schema = "orders")
class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column
    private String reason;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    protected OrderEventEntity() {
        // JPA
    }

    OrderEventEntity(String fromStatus, String toStatus, String reason) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.at = Instant.now();
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    String getToStatus() {
        return toStatus;
    }

    Instant getAt() {
        return at;
    }
}
