package com.townbasket.notifications.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code notifications.notification_log}. Module-internal.
 * Best-effort audit of what was pushed; a logging failure never blocks an order
 * transition.
 */
@Entity
@Table(name = "notification_log", schema = "notifications")
class NotificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String type;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected NotificationLogEntity() {
        // JPA
    }

    NotificationLogEntity(Long orderId, String channel, String type) {
        this.orderId = orderId;
        this.channel = channel;
        this.type = type;
    }

    Long getId() {
        return id;
    }
}
