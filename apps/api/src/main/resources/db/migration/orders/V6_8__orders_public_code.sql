-- A short, human-speakable order code — the number customers quote and staff
-- read back over the phone. Replaces the sequential numeric id as the
-- CUSTOMER-VISIBLE order number for two reasons:
--
--   1. The sequential id publishes the store's order volume: any customer who
--      places two orders a week apart can subtract the numbers and read the
--      weekly throughput straight off them.
--   2. The other unguessable identifier we have (public_token, V6_3) is a
--      36-character UUID — fine in a URL, useless on a phone call.
--
-- 8 characters of Crockford base32 (~40 bits): the alphabet deliberately
-- excludes I, L, O and U so a spoken or handwritten code is unambiguous.
-- Uniqueness is enforced here; the generator retries on collision.
--
-- This is a DISPLAY identifier, not a capability: order access is authorised by
-- login + ownership (see OrderService), and public_token remains the URL handle.

ALTER TABLE orders.orders
    ADD COLUMN public_code TEXT;

-- Backfill existing rows one at a time so each gets its own draw (a single
-- uncorrelated subquery would be evaluated once and assign every row the same
-- code). random() rather than a CSPRNG is deliberate and sufficient here: this
-- column is a display label, not an access token, and the loop below re-draws
-- on collision. New rows get their code from SecureRandom in the application
-- (see OrderCodes) — pgcrypto is not installed, and adding an extension just
-- for a one-off backfill of legacy rows would not earn its keep.
DO $$
DECLARE
    alphabet CONSTANT TEXT := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    target   RECORD;
    code     TEXT;
    i        INT;
BEGIN
    FOR target IN SELECT id FROM orders.orders WHERE public_code IS NULL LOOP
        LOOP
            code := '';
            FOR i IN 1..8 LOOP
                -- random() is [0,1), so the index lands in 1..32.
                code := code || substr(alphabet, 1 + floor(random() * 32)::int, 1);
            END LOOP;
            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM orders.orders WHERE public_code = code);
        END LOOP;
        UPDATE orders.orders SET public_code = code WHERE id = target.id;
    END LOOP;
END $$;

ALTER TABLE orders.orders
    ALTER COLUMN public_code SET NOT NULL;

ALTER TABLE orders.orders
    ADD CONSTRAINT uq_orders_public_code UNIQUE (public_code);
