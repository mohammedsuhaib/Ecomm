-- M4: tie orders to a user when a valid Bearer token is present at checkout.
-- Nullable: guest orders (no token) keep user_id NULL. The identity user id is
-- a plain id (no cross-schema FK — schema-per-module rule).
ALTER TABLE orders.orders ADD COLUMN user_id BIGINT;
CREATE INDEX idx_orders_user_id ON orders.orders (user_id);
