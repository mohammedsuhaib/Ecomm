-- The shelf price (MRP) of each ordered item as it stood at the moment of sale,
-- beside the `unit_price` actually charged, so an order can say how much the
-- customer saved without looking at today's catalogue (prices and MRPs change).
--
-- Nullable: orders placed before this column existed have no recorded MRP, and
-- so show no saving. It is deliberately NOT backfilled from the catalogue, which
-- would put today's MRP on a past sale.
ALTER TABLE orders.order_items
    ADD COLUMN mrp NUMERIC(10,2);
