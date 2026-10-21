-- Units delivered per product, the measure behind the storefront's "popular"
-- ordering. Kept here, as a counter the catalog owns, because the catalog may
-- not read the orders schema: orders announces each delivery (OrderDelivered,
-- with its lines) and a listener in this module adds to the counter.
-- Existing orders are folded in by orders V6_15, which runs after both schemas exist.
ALTER TABLE catalog.products ADD COLUMN sold_count BIGINT NOT NULL DEFAULT 0;
