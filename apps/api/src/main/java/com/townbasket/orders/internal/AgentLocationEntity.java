package com.townbasket.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code orders.agent_locations} — a rider's CURRENT position,
 * one row per agent, overwritten on every ping. Module-internal.
 *
 * <p>Read-side only. Writes go through
 * {@link AgentLocationRepository#upsert}, a single {@code INSERT … ON CONFLICT}
 * statement, because a ping is fire-and-forget from a phone on a patchy
 * connection and a load-then-save round trip buys nothing here.
 */
@Entity
@Table(name = "agent_locations", schema = "orders")
class AgentLocationEntity {

    /** identity.users id of the rider — assigned, not generated. */
    @Id
    @Column(name = "agent_id")
    private Long agentId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "accuracy_m")
    private Double accuracyMeters;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AgentLocationEntity() {
        // JPA
    }

    Long getAgentId() {
        return agentId;
    }

    double getLat() {
        return lat;
    }

    double getLng() {
        return lng;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }
}
