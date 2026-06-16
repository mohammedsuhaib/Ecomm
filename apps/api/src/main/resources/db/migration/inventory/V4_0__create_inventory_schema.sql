-- inventory module schema (schema-per-module rule).
-- Owns: stock_levels, stock_movements (audit ledger), reservations. Fleshed out in M3.
CREATE SCHEMA IF NOT EXISTS inventory;
