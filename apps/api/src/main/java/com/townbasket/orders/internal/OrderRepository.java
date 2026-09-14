package com.townbasket.orders.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     *
     * <p>The inner subquery collapses the events to one DELIVERED timestamp
     * per order (the first): {@code transition()} has no locking, so two
     * concurrent DELIVERED requests can both commit an event row, and without
     * the dedup each such order would count twice — doubling its money in
     * this report.
     *
     * <p>Note this metric is delivered-order value by <em>delivery</em> date;
     * it deliberately differs from the analytics module's daily revenue,
     * which buckets by placed-at (IST) and excludes CANCELLED orders. The two
     * figures are not expected to reconcile.
     */
    @Query(value = """
            SELECT o.assigned_agent_id           AS "agentId",
                   CAST(d.delivered_at AS date)  AS "day",
                   COUNT(*)                      AS "deliveries",
                   SUM(o.total)                  AS "amount"
            FROM (
                SELECT order_id, MIN(at) AS delivered_at
                FROM orders.order_events
                WHERE to_status = 'DELIVERED'
                  AND at >= :since
                GROUP BY order_id
            ) d
            JOIN orders.orders o ON o.id = d.order_id
            WHERE o.assigned_agent_id IS NOT NULL
            GROUP BY o.assigned_agent_id, CAST(d.delivered_at AS date)
            ORDER BY "day" DESC, "agentId"
            """, nativeQuery = true)
    List<AgentDeliveryRow> countDeliveredByAgentAndDay(@Param("since") Instant since);

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderEntity> findByPublicToken(UUID publicToken);

    Page<OrderEntity> findAllByOrderByPlacedAtDescIdDesc(Pageable pageable);

    Page<OrderEntity> findByStatusOrderByPlacedAtDescIdDesc(OrderStatus status, Pageable pageable);

    /**
     * Admin search: order code, phone, customer name, or the internal numeric id.
     *
     * <p>{@code like} arrives lower-cased with wildcards and escaping already applied
     * by the caller. {@code codeLike} is the same term folded through
     * {@link OrderCodes#normalize} first, so a customer who dictates "o" for zero or
     * types a hyphen still finds their order; when the term can't be a code at all the
     * caller passes a pattern that matches nothing. {@code idExact} matches the
     * numeric id by EQUALITY and is null unless the term is all digits.
     *
     * <p>Native, and shaped as a MATERIALIZED CTE, for a specific measured reason.
     * The predicates are all leading-wildcard {@code LIKE}s, which the GIN trigram
     * indexes from V6_10 serve well — combining them takes 0.13 ms on a 50 000-order
     * table. But written as one flat statement with {@code ORDER BY placed_at DESC
     * LIMIT 20}, the planner instead walks the placed_at index hoping to fill the
     * limit early, and because trigram selectivity is badly estimated it walks the
     * WHOLE index: 33 ms, discarding 49 995 rows to return 5. MATERIALIZED forces the
     * filter to resolve through the indexes first, so only the matches are sorted.
     * Two branches of one OR chain cannot use indexes unless every branch can, which
     * is why the id is an equality test rather than the substring cast it once was.
     */
    @Query(value = """
            WITH matches AS MATERIALIZED (
                SELECT o.id FROM orders.orders o
                WHERE lower(o.public_code) LIKE :codeLike ESCAPE '\\'
                   OR o.phone LIKE :like ESCAPE '\\'
                   OR lower(o.customer_name) LIKE :like ESCAPE '\\'
                   OR (CAST(:idExact AS bigint) IS NOT NULL AND o.id = CAST(:idExact AS bigint))
            )
            SELECT o.* FROM orders.orders o
            JOIN matches m ON m.id = o.id
            ORDER BY o.placed_at DESC, o.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM orders.orders o
            WHERE lower(o.public_code) LIKE :codeLike ESCAPE '\\'
               OR o.phone LIKE :like ESCAPE '\\'
               OR lower(o.customer_name) LIKE :like ESCAPE '\\'
               OR (CAST(:idExact AS bigint) IS NOT NULL AND o.id = CAST(:idExact AS bigint))
            """,
            nativeQuery = true)
    Page<OrderEntity> search(
            @Param("like") String like, @Param("codeLike") String codeLike,
            @Param("idExact") Long idExact, Pageable pageable);

    /**
     * The same search narrowed to one status. {@code status} is bound as the enum's
     * name because this is native SQL; see {@link #search} for the CTE's rationale.
     */
    @Query(value = """
            WITH matches AS MATERIALIZED (
                SELECT o.id FROM orders.orders o
                WHERE o.status = :status
                  AND (lower(o.public_code) LIKE :codeLike ESCAPE '\\'
                       OR o.phone LIKE :like ESCAPE '\\'
                       OR lower(o.customer_name) LIKE :like ESCAPE '\\'
                       OR (CAST(:idExact AS bigint) IS NOT NULL AND o.id = CAST(:idExact AS bigint)))
            )
            SELECT o.* FROM orders.orders o
            JOIN matches m ON m.id = o.id
            ORDER BY o.placed_at DESC, o.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM orders.orders o
            WHERE o.status = :status
              AND (lower(o.public_code) LIKE :codeLike ESCAPE '\\'
                   OR o.phone LIKE :like ESCAPE '\\'
                   OR lower(o.customer_name) LIKE :like ESCAPE '\\'
                   OR (CAST(:idExact AS bigint) IS NOT NULL AND o.id = CAST(:idExact AS bigint)))
            """,
            nativeQuery = true)
    Page<OrderEntity> searchByStatus(
            @Param("status") String status, @Param("like") String like,
            @Param("codeLike") String codeLike, @Param("idExact") Long idExact,
            Pageable pageable);

    Page<OrderEntity> findByUserIdOrderByPlacedAtDescIdDesc(Long userId, Pageable pageable);

    Page<OrderEntity> findByAssignedAgentIdOrderByPlacedAtDescIdDesc(Long agentId, Pageable pageable);

    Page<OrderEntity> findByAssignedAgentIdAndStatusOrderByPlacedAtDescIdDesc(
            Long agentId, OrderStatus status, Pageable pageable);
}
