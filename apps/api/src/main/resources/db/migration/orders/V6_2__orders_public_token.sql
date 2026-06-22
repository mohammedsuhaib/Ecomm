-- Privacy hardening: give each order an unguessable public token used for the
-- customer-facing fetch + live-tracking endpoints, so order details (delivery
-- OTP, name, phone, address) can no longer be harvested by enumerating the
-- sequential numeric id. The numeric id stays internal/admin-facing.
--
-- Backfill existing rows with random UUIDs, then enforce NOT NULL + UNIQUE.

ALTER TABLE orders.orders
    ADD COLUMN public_token UUID;

UPDATE orders.orders
    SET public_token = gen_random_uuid()
    WHERE public_token IS NULL;

ALTER TABLE orders.orders
    ALTER COLUMN public_token SET NOT NULL;

ALTER TABLE orders.orders
    ADD CONSTRAINT uq_orders_public_token UNIQUE (public_token);
