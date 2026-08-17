-- Amul Dahi now has a real asset in the storefront (V3.6 left image_url NULL
-- because none existed). Same path convention as V3.4.

UPDATE catalog.products
SET image_url = '/images/products/' || slug || '.jpg'
WHERE slug = 'amul-dahi' AND image_url IS NULL;
