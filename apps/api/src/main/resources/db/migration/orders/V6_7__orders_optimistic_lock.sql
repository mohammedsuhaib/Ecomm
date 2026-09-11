-- Concurrency control for the order state machine (transition() was
-- read-check-write with no lock, so two concurrent identical transitions
-- could both commit — duplicating the event row and the outbox publications).
--
-- 1. version column backing JPA @Version optimistic locking on orders.orders.
ALTER TABLE orders.orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2. DB-level backstop for the terminal money transition: an order can be
--    marked DELIVERED at most once, even if a future code path writes an
--    event row without touching (and version-bumping) the order row.
--    The race predates this migration, so dedupe first — keep the earliest
--    DELIVERED row per order (what the delivery stats count via MIN(at)).
DELETE FROM orders.order_events
WHERE to_status = 'DELIVERED'
  AND id NOT IN (SELECT MIN(id) FROM orders.order_events
                 WHERE to_status = 'DELIVERED'
                 GROUP BY order_id);

CREATE UNIQUE INDEX uq_order_events_delivered_once
    ON orders.order_events (order_id)
    WHERE to_status = 'DELIVERED';
