-- One-off: count what was already delivered before catalog.products.sold_count
-- (catalog V3_14) existed. Lives in the orders band so it runs after both
-- schemas are in place on a fresh database; later deliveries are counted by the
-- catalog's own OrderDelivered listener, never by this.
UPDATE catalog.products p
SET sold_count = s.units
FROM (
    SELECT v.product_id, SUM(i.qty) AS units
    FROM orders.order_items i
    JOIN orders.orders o ON o.id = i.order_id AND o.status = 'DELIVERED'
    JOIN catalog.product_variants v ON v.id = i.variant_id
    GROUP BY v.product_id
) s
WHERE p.id = s.product_id;
