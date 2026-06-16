-- identity module owns its own Postgres schema (schema-per-module rule).
-- Owns: users, addresses, refresh_tokens. M1 establishes the schema only;
-- table columns are fleshed out in M4 (phone OTP, JWTs, roles).
CREATE SCHEMA IF NOT EXISTS identity;
