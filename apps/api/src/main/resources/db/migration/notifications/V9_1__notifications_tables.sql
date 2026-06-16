-- notifications module tables (schema `notifications`).
-- Owns: notification_log (core SSE channel). push_subscriptions arrives with
-- the Web Push add-on. order_id is a plain id; no cross-schema FK.

CREATE TABLE notifications.notification_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    channel    TEXT        NOT NULL,           -- SSE (core)
    type       TEXT        NOT NULL,           -- ORDER_PLACED | ORDER_CONFIRMED | STATUS_CHANGED | ...
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_log_order_id ON notifications.notification_log (order_id);
