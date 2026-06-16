-- payments module tables (schema `payments`).
-- Owns: payments. (payment_webhook_log arrives with the live Paytm flow in M5.)
--
-- order_id is a plain id into the orders module (no cross-schema FK). All tables
-- qualified with `payments.`.

CREATE TABLE payments.payments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    method     TEXT          NOT NULL,           -- COD | UPI
    status     TEXT          NOT NULL,           -- PENDING | PAID | FAILED | COD_PENDING
    amount     NUMERIC(10,2) NOT NULL,
    reference  TEXT,                              -- provider txn reference (nullable for COD)
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id ON payments.payments (order_id);
