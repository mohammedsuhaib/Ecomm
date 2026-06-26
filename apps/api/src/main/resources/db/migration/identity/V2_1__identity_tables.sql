-- identity module tables (schema `identity`).
-- Owns: users, addresses, refresh_tokens. All tables qualified with `identity.`.
--
-- Roles: CUSTOMER (phone-OTP login via Firebase, our own JWTs) and
-- STORE_STAFF / ADMIN (email + password). Refresh tokens are stored as a
-- SHA-256 hash only (the raw value is returned to the client once, at issuance)
-- and rotate on use. Addresses and refresh_tokens carry a plain `user_id`
-- column (no JPA collection on the user) and are queried by user id.

CREATE TABLE identity.users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role          TEXT        NOT NULL,
    phone         TEXT        UNIQUE,
    email         TEXT        UNIQUE,
    password_hash TEXT,
    name          TEXT,
    firebase_uid  TEXT        UNIQUE,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE identity.addresses (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT           NOT NULL REFERENCES identity.users (id),
    label      TEXT,
    line       TEXT             NOT NULL,
    lat        DOUBLE PRECISION NOT NULL,
    lng        DOUBLE PRECISION NOT NULL,
    is_default BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE identity.refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES identity.users (id),
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_user_id ON identity.addresses (user_id);
CREATE INDEX idx_refresh_tokens_user_id ON identity.refresh_tokens (user_id);
