-- serviceability module tables (schema `serviceability`).
-- Owns: stores. Distance check uses the Haversine formula against the active store.
-- Multi-store data model, single-store operation (ARCHITECTURE §1.5).

CREATE TABLE serviceability.stores (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              TEXT             NOT NULL,
    address           TEXT             NOT NULL,
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    delivery_radius_m INT              NOT NULL DEFAULT 5000,
    opening_time      TIME             NOT NULL,
    closing_time      TIME             NOT NULL,
    min_order_value   NUMERIC(10,2)    NOT NULL DEFAULT 499,
    active            BOOLEAN          NOT NULL DEFAULT TRUE
);
