-- Belt-and-braces local init. The townbasket database/role are created by the
-- postgres image from POSTGRES_* env vars; the per-module schemas are created
-- by Flyway migrations in the API (schema-per-module rule). This file exists as
-- the documented hook for any local-only seed/extension setup.

-- Trigram extension is used by the catalog full-text search (M2). Creating it
-- here keeps local dev aligned with prod without putting it in a module
-- migration that other modules must not depend on.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
