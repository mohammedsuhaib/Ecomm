-- Dev/demo seed: give every seeded catalog variant a healthy on-hand quantity
-- so M3 orders can be placed end-to-end.
--
-- store_id is fixed to 1: the single MVP store is seeded as identity id 1 by the
-- serviceability seed (V8_2). We use the literal id (rather than joining
-- serviceability.stores) because Flyway applies migrations in global version
-- order — V4_2 runs BEFORE V8_2, so the store row does not exist yet at this
-- point. This is a one-off SEED, not a runtime cross-schema join.
--
-- Reads catalog.product_variants (seeded earlier at V3_2). Idempotent via the
-- (store_id, variant_id) unique key.

INSERT INTO inventory.stock_levels (store_id, variant_id, on_hand, reserved, low_stock_threshold)
SELECT 1, v.id, 100, 0, 5
FROM catalog.product_variants v
ON CONFLICT (store_id, variant_id) DO NOTHING;
