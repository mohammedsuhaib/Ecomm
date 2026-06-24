-- Kannada (kn) transliteration of the product name, shown to customers browsing
-- in Kannada. Nullable so product inserts/imports never have to supply it; the
-- catalog transliteration backfill job (ProductNameBackfillJob) fills the blanks
-- and the storefront falls back to `name` whenever it is null.
ALTER TABLE catalog.products
    ADD COLUMN IF NOT EXISTS name_kn TEXT;
