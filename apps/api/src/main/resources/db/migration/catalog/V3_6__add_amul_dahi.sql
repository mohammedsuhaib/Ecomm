-- Follow-on dev/demo seed: adds the dairy "Amul Dahi" product (alongside the
-- existing Nestle Dahi) with 200 g and 400 g variants.
--
-- This re-expresses an earlier change that was (incorrectly) made by editing the
-- already-applied V3_2 seed; it is delivered here as a new migration so existing
-- databases keep a stable V3_2 checksum and `flyway validate` still passes.
--
-- Idempotent: the product guards on slug uniqueness via ON CONFLICT DO NOTHING
-- and the variants insert only when the (product, label) pair is absent, so a
-- re-apply cannot create duplicates. Prices are in INR; cost_price is
-- internal-only (gross-profit reporting), matching V3_2's conventions.

-- image_url is set here (most products leave it NULL for now). Served from the
-- storefront's /public for local/dev; swap to the DO Spaces URL in production.
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, image_url, available)
SELECT id, 'Amul Dahi', 'amul-dahi',
       'Toned milk dahi — creamy and tasty.', TRUE,
       '/products/amul-dahi.jpg', TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

-- Variants (selling_price, cost_price [internal], mrp). Insert only if the
-- (product, label) pair does not yet exist.
INSERT INTO catalog.product_variants (product_id, label, selling_price, cost_price, mrp, available, sort_order)
SELECT p.id, v.label, v.selling_price, v.cost_price, v.mrp, TRUE, v.sort_order
FROM (VALUES
    -- product_slug, label, selling, cost, mrp, sort
    ('amul-dahi', '200 g', 25.00, 20.00, 28.00, 1),
    ('amul-dahi', '400 g', 45.00, 38.00, 50.00, 2)
) AS v(product_slug, label, selling_price, cost_price, mrp, sort_order)
JOIN catalog.products p ON p.slug = v.product_slug
WHERE NOT EXISTS (
    SELECT 1 FROM catalog.product_variants pv
    WHERE pv.product_id = p.id AND pv.label = v.label
);
