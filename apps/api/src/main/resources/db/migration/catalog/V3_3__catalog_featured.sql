-- B1: add a `featured` flag to products to drive the storefront "Popular picks".
-- Defaults FALSE so existing rows are unaffected; the UPDATE then promotes a
-- curated STARTER MIX spread across categories, favouring higher-value/popular
-- items to lift average cart value.
--
-- Idempotent-friendly: ADD COLUMN IF NOT EXISTS keeps a re-apply safe, and the
-- UPDATE simply re-sets the same flags (no duplicate rows possible).

ALTER TABLE catalog.products
    ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;

-- Curated featured mix (8 products, one+ per major category, leaning to
-- higher-value staples). Slugs reference real rows from V3_2 seed.
UPDATE catalog.products
SET featured = TRUE
WHERE slug IN (
    'aashirvaad-whole-wheat-atta', -- Atta & Flours (5/10 kg, high value)
    'india-gate-basmati-rice',     -- Rice & Dals (premium basmati)
    'amul-pure-ghee',              -- Edible Oils & Ghee (premium ghee)
    'fortune-sunflower-oil',       -- Edible Oils & Ghee (staple oil)
    'amul-butter',                 -- Dairy (popular)
    'tata-tea-premium',            -- Beverages (everyday staple)
    'surf-excel-detergent-powder', -- Cleaning & Household (high value)
    'parle-g-biscuits'             -- Biscuits & Snacks (popular impulse buy)
);
