-- Variants created after the V4_2 seed (admin "add variant", CSV import) never
-- got a stock_levels row, so they were invisible in the admin stock list and
-- unbuyable. Backfill a zero row for every variant missing one; from now on the
-- catalog's VariantCreated event opens the row at creation time.
--
-- store_id 1 = the single MVP store (same literal as V4_2, same rationale).
-- One-off backfill, not a runtime cross-schema join. Idempotent via the
-- (store_id, variant_id) unique key.

INSERT INTO inventory.stock_levels (store_id, variant_id, on_hand, reserved, low_stock_threshold)
SELECT 1, v.id, 0, 0, 5
FROM catalog.product_variants v
ON CONFLICT (store_id, variant_id) DO NOTHING;
