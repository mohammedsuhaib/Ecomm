-- GST invoice numbering, as its own series rather than a view of the order id.
--
-- Rule 46(b) of the CGST Rules wants a tax invoice to carry a consecutive
-- serial number, at most sixteen characters, unique for a FINANCIAL YEAR
-- (1 April - 31 March in India). The old "INV-<order id>" satisfied none of
-- that: one global sequence that never resets in April, and every cancelled or
-- never-invoiced order silently burning a number.
--
-- So:
--   * invoice_series is a per-FY counter, incremented inside the issuing
--     transaction. A Postgres SEQUENCE would be wrong here — sequences are
--     non-transactional, so a rolled-back issue would leave a permanent gap in
--     a series that is supposed to be consecutive.
--   * invoice_number / invoiced_at are assigned ONCE, when the invoice is first
--     issued (never at insert time), and are immutable afterwards so a
--     re-download reproduces the same document. Both stay NULL until then;
--     Postgres allows many NULLs under a UNIQUE constraint, which is exactly
--     the shape we want.
--   * Numbering by issue time (not order time) keeps the series chronological
--     within its financial year by construction.

CREATE TABLE orders.invoice_series (
    -- Financial-year label, e.g. '25-26' for 1 Apr 2025 - 31 Mar 2026.
    fy       TEXT   PRIMARY KEY,
    last_seq BIGINT NOT NULL DEFAULT 0 CHECK (last_seq >= 0)
);

COMMENT ON TABLE orders.invoice_series IS
    'Per-financial-year consecutive counter backing GST invoice numbers (CGST Rule 46(b)).';

ALTER TABLE orders.orders
    ADD COLUMN invoice_number TEXT,
    ADD COLUMN invoiced_at    TIMESTAMPTZ;

ALTER TABLE orders.orders
    ADD CONSTRAINT uq_orders_invoice_number UNIQUE (invoice_number);

-- Both columns are set together or not at all.
ALTER TABLE orders.orders
    ADD CONSTRAINT ck_orders_invoiced_together CHECK (
        (invoice_number IS NULL AND invoiced_at IS NULL)
        OR (invoice_number IS NOT NULL AND invoiced_at IS NOT NULL));

-- Existing orders are deliberately NOT backfilled: an invoice number records
-- that a document was issued, and inventing numbers retroactively for orders
-- whose invoices were already handed out as "INV-<id>" would corrupt the
-- series. They pick one up the next time an invoice is issued for them.
