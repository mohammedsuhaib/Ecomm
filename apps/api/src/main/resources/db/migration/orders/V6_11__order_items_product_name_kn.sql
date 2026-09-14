-- Kannada name of the ordered product, snapshotted at the moment of sale beside
-- the English `product_name`.
--
-- Snapshotted rather than looked up: an order line is a record of what was sold,
-- so a later rename in the catalog must not rewrite a past order — the same
-- reason `product_name`, `unit_price` and the GST fields are already frozen here.
--
-- Nullable, and NOT backfilled. Existing orders were placed against whatever the
-- catalog held then, and inventing a Kannada name for them now would be writing
-- history that did not happen; the storefront falls back to `product_name`
-- exactly as it does for a product the transliteration backfill has not reached.
--
-- The GST invoice deliberately keeps using `product_name` (see InvoiceService):
-- a statutory document stays in one language.
ALTER TABLE orders.order_items
    ADD COLUMN IF NOT EXISTS product_name_kn TEXT;
