-- READY_FOR_DELIVERY joins the order state machine: packed and bagged, waiting
-- on the counter for a rider to collect (the rider's own pick-up is what moves
-- it on to OUT_FOR_DELIVERY).
--
-- No schema change is needed for it — orders.status is TEXT with no CHECK
-- constraint, so the new value is already storable. What this migration fixes is
-- the documentation: the status list lives in an inline comment in
-- V6_1__orders_tables.sql, which cannot be edited once applied (Flyway
-- checksums), so anyone reading the schema would be told the old set. A real
-- column comment can be updated by a later migration, which is what this is.
COMMENT ON COLUMN orders.orders.status IS
    'Order state machine: PLACED | CONFIRMED | PACKING | READY_FOR_DELIVERY | OUT_FOR_DELIVERY | DELIVERY_FAILED | DELIVERED | CANCELLED';
