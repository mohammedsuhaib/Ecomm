package com.townbasket.orders.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Module-internal Spring Data repository for orders. */
interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /** Projection for {@link #countDeliveredByAgentAndDay()}. */
    interface AgentDeliveryRow {
        Long getAgentId();
        LocalDate getDay();
        long getDeliveries();
        BigDecimal getAmount();
    }

    /**
     * Per-agent, per-date delivered-order counts and summed order value from
     * the order-events audit trail. The delivered date is the UTC calendar
     * date of the DELIVERED transition — the store operates 08:00–21:00 IST,
     * a window inside which the UTC and IST calendar dates always coincide,
     * so no zone shift needed.
     */
    @Query(value = """
            SELECT o.assigned_agent_id AS "agentId",
                   CAST(e.at AS date)  AS "day",
                   COUNT(*)            AS "deliveries",
                   SUM(o.total)        AS "amount"
            FROM orders.order_events e
            JOIN orders.orders o ON o.id = e.order_id
            WHERE e.to_status = 'DELIVERED' AND o.assigned_agent_id IS NOT NULL
            GROUP BY o.assigned_agent_id, CAST(e.at AS date)
            ORDER BY "day" DESC, "agentId"
            """, nativeQuery = true)
    List<AgentDeliveryRow> countDeliveredByAgentAndDay();

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderEntity> findByPublicToken(UUID publicToken);

    Page<OrderEntity> findAllByOrderByPlacedAtDescIdDesc(Pageable pageable);

    Page<OrderEntity> findByStatusOrderByPlacedAtDescIdDesc(OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByUserIdOrderByPlacedAtDescIdDesc(Long userId, Pageable pageable);

    Page<OrderEntity> findByAssignedAgentIdOrderByPlacedAtDescIdDesc(Long agentId, Pageable pageable);

    Page<OrderEntity> findByAssignedAgentIdAndStatusOrderByPlacedAtDescIdDesc(
            Long agentId, OrderStatus status, Pageable pageable);
}
