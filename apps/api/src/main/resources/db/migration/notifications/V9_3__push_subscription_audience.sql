-- Who a push subscription is for.
--
-- 'USER'  — a customer's or rider's own device: they are pushed the updates
--           about THEIR orders / jobs (the original and default behaviour).
-- 'STAFF' — a device on which store staff turned on dashboard alerts: it is
--           pushed the store-wide events (a new order, a failed delivery),
--           whoever's order it is.
--
-- A column rather than a lookup of the owner's role, because the notifications
-- module must not reach into identity's tables, and because an ADMIN also uses
-- the rider app: which app subscribed is what decides, not who they are.

ALTER TABLE notifications.push_subscriptions
    ADD COLUMN audience TEXT NOT NULL DEFAULT 'USER'
        CONSTRAINT chk_push_subscriptions_audience CHECK (audience IN ('USER', 'STAFF'));

CREATE INDEX idx_push_subscriptions_audience ON notifications.push_subscriptions (audience);
