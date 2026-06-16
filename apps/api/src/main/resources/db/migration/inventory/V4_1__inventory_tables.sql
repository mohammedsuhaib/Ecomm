-- inventory module tables (schema `inventory`).
-- Owns: stock_levels, reservations, stock_movements (audit ledger).
--
-- Cross-module rule: variant_id / order_id / store_id are plain ids; there are
-- NO foreign keys across schemas (each module owns its schema; references are
-- by id only). All tables are qualified with `inventory.` because Flyway runs
-- with default schema `flyway`.

-- Stock per (store_id, variant_id): on_hand and the currently-reserved amount.
CREATE TABLE inventory.stock_levels (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id            BIGINT  NOT NULL,
    variant_id          BIGINT  NOT NULL,
    on_hand             INT     NOT NULL DEFAULT 0,
    reserved            INT     NOT NULL DEFAULT 0,
    low_stock_threshold INT     NOT NULL DEFAULT 5,
    UNIQUE (store_id, variant_id)
);

CREATE INDEX idx_stock_levels_variant_id ON inventory.stock_levels (variant_id);

-- One row per reserved order line. Status tracks the reservation lifecycle:
-- RESERVED -> COMMITTED (on delivery) | RELEASED (on cancellation).
CREATE TABLE inventory.reservations (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    variant_id BIGINT      NOT NULL,
    qty        INT         NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reservations_order_id ON inventory.reservations (order_id);

-- Append-only audit ledger of every stock movement (reserve/commit/release/adjust).
CREATE TABLE inventory.stock_movements (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    variant_id BIGINT      NOT NULL,
    delta      INT         NOT NULL,
    reason     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_variant_id ON inventory.stock_movements (variant_id);
