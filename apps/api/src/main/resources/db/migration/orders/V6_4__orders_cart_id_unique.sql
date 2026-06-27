-- Enforce one order per cart at the database level.
--
-- A cart is single-use: once it has been turned into an order it is marked
-- checked_out and can never be ordered again. The application already guards
-- this (the checkedOut flag + the per-key idempotency lookup), but two requests
-- that race on the SAME cart under DIFFERENT idempotency keys can both pass the
-- in-transaction checkedOut check before either commits, creating two orders for
-- one cart. This partial unique index closes that window: the second commit
-- fails with a constraint violation (surfaced as 409 by GlobalExceptionHandler)
-- instead of silently double-ordering.
--
-- Partial (WHERE cart_id IS NOT NULL) so it never constrains rows without a cart
-- id, and idempotent (IF NOT EXISTS) for safe re-runs.

CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_cart_id
    ON orders.orders (cart_id)
    WHERE cart_id IS NOT NULL;
