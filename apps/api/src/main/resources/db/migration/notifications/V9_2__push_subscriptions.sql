-- Web Push subscriptions (the browser-push notification channel).
--
-- One row per browser/device a customer has granted notification permission on;
-- a person may have several (phone + desktop). `endpoint` is the push service
-- URL the browser mints — it is unguessable and identifies the subscription, so
-- it carries a UNIQUE constraint and doubles as the unsubscribe key.
--
-- p256dh + auth are the subscription's public key material, used to encrypt each
-- payload (RFC 8291). They are per-subscription secrets, not account credentials.
--
-- user_id is a plain id (no cross-schema FK, per the schema-per-module rule).

CREATE TABLE notifications.push_subscriptions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    endpoint     TEXT        NOT NULL UNIQUE,
    p256dh       TEXT        NOT NULL,
    auth         TEXT        NOT NULL,
    user_agent   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_push_subscriptions_user_id ON notifications.push_subscriptions (user_id);
