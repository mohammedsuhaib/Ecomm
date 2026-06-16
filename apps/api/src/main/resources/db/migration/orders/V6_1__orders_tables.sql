-- orders module tables (schema `orders`).
-- Owns: orders, order_items, order_events (audit trail / timeline).
--
-- Cross-module ids (cart_id, store_id, variant_id) are plain ids; no cross-
-- schema FKs. All tables qualified with `orders.`.
--
-- order_items.cost_price is an INTERNAL COGS snapshot for future analytics and
-- must NEVER be returned in any order API response.

CREATE TABLE orders.orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id         UUID,
    store_id        BIGINT        NOT NULL,
    customer_name   TEXT          NOT NULL,
    phone           TEXT          NOT NULL,
    address_line    TEXT          NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    payment_method  TEXT          NOT NULL,           -- COD | UPI
    payment_status  TEXT          NOT NULL,           -- COD_PENDING | PAID | FAILED | PENDING
    status          TEXT          NOT NULL,           -- PLACED | CONFIRMED | PACKING | OUT_FOR_DELIVERY | DELIVERED | CANCELLED
    subtotal        NUMERIC(10,2) NOT NULL,
    total           NUMERIC(10,2) NOT NULL,
    delivery_otp    TEXT          NOT NULL,
    idempotency_key TEXT          NOT NULL UNIQUE,
    placed_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_status ON orders.orders (status);
CREATE INDEX idx_orders_placed_at ON orders.orders (placed_at DESC);

CREATE TABLE orders.order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders.orders (id),
    variant_id   BIGINT        NOT NULL,
    product_name TEXT          NOT NULL,
    label        TEXT          NOT NULL,
    unit_price   NUMERIC(10,2) NOT NULL,
    cost_price   NUMERIC(10,2) NOT NULL,             -- INTERNAL COGS snapshot — never exposed via API
    qty          INT           NOT NULL,
    line_total   NUMERIC(10,2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON orders.order_items (order_id);

CREATE TABLE orders.order_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders.orders (id),
    from_status TEXT,
    to_status   TEXT        NOT NULL,
    reason      TEXT,
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_events_order_id ON orders.order_events (order_id);
