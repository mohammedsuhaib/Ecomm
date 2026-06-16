package com.townbasket.payments.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for {@code payments.payments}. Module-internal. {@code method} and
 * {@code status} are stored as their enum names.
 */
@Entity
@Table(name = "payments", schema = "payments")
class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column
    private String reference;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEntity() {
        // JPA
    }

    PaymentEntity(Long orderId, String method, String status, BigDecimal amount, String reference) {
        this.orderId = orderId;
        this.method = method;
        this.status = status;
        this.amount = amount;
        this.reference = reference;
    }

    Long getId() {
        return id;
    }

    String getStatus() {
        return status;
    }

    String getReference() {
        return reference;
    }
}
