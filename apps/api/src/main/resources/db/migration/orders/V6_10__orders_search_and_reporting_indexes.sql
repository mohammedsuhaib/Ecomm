-- Indexes for the three order queries that were reading far more rows than they
-- return. Measured on a 50 000-order table (about 18 months at this store's
-- ~100 orders/day) before adding them.
--
-- 1. ANALYTICS. Every analytics endpoint filters `WHERE store_id = :storeId`,
--    and there was no index on store_id at all, so each admin dashboard load
--    sequentially scanned orders. The composite with placed_at serves both the
--    filter and the date-range/ordering the reports apply on top of it.
--
-- 2. ADMIN ORDER SEARCH. "A customer is on the phone about their order" matches
--    the order code, phone, or customer name with leading wildcards
--    (`LIKE '%term%'`). No btree index can serve that shape: the query walked
--    the whole table via placed_at and discarded 49 995 rows to return 5 (~33 ms,
--    plus another ~30 ms for the pager's COUNT running the same predicates).
--    Trigram GIN indexes are exactly the right tool for leading-wildcard
--    contains, and pg_trgm is already installed (catalog V3_1) but was used by
--    only one index in the whole database.
--
--    Note these are on lower(...) to match the query's own lower(...) — an
--    expression index only helps when the expression matches exactly, which is
--    why the products name index could never serve the admin product search
--    that generated upper(name).
--
-- 3. RIDER QUEUE. idx_orders_assigned_agent is single-column, though the
--    migration that added it (V6_5) describes it as "assigned_agent_id +
--    status". The query really does filter on both, so make the index match
--    what its own comment always claimed.

CREATE INDEX idx_orders_store_placed_at
    ON orders.orders (store_id, placed_at DESC);

CREATE INDEX idx_orders_public_code_trgm
    ON orders.orders USING gin (lower(public_code) gin_trgm_ops);

CREATE INDEX idx_orders_phone_trgm
    ON orders.orders USING gin (phone gin_trgm_ops);

CREATE INDEX idx_orders_customer_name_trgm
    ON orders.orders USING gin (lower(customer_name) gin_trgm_ops);

CREATE INDEX idx_orders_agent_status
    ON orders.orders (assigned_agent_id, status);

-- Superseded by idx_orders_agent_status, which serves every query the
-- single-column index did (assigned_agent_id is its leading column).
DROP INDEX IF EXISTS orders.idx_orders_assigned_agent;

-- The delivered-date report takes MIN(at) per order over DELIVERED events. The
-- existing partial unique index covers order_id but not `at`, so every delivered
-- order needed a heap fetch to read the timestamp; including it makes the
-- aggregate index-only.
CREATE INDEX idx_order_events_delivered_at
    ON orders.order_events (order_id, at)
    WHERE to_status = 'DELIVERED';
