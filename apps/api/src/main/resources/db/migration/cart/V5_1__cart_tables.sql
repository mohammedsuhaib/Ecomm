-- cart module tables (schema `cart`).
-- Owns: carts, cart_items.
--
-- M3 carts are anonymous (no auth until M4): a cart is keyed by a server-
-- generated UUID handed back to the client. variant_id is a plain id into the
-- catalog (no cross-schema FK). All tables qualified with `cart.`.

CREATE TABLE cart.carts (
    id          UUID        NOT NULL PRIMARY KEY,
    checked_out BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart.cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    UUID   NOT NULL REFERENCES cart.carts (id),
    variant_id BIGINT NOT NULL,
    qty        INT    NOT NULL,
    UNIQUE (cart_id, variant_id)
);

CREATE INDEX idx_cart_items_cart_id ON cart.cart_items (cart_id);
