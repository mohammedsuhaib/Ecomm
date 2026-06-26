-- Real product front photos for the demo catalogue.
--
-- Images are sourced from Open Food Facts (open data, ODbL) and self-hosted by
-- the storefront under /public/images/products/<slug>.jpg (served at
-- /images/products/<slug>.jpg). Setting image_url makes ProductThumb render the
-- real photo instead of the per-category emoji fallback. Every product below has
-- a downloaded asset committed alongside this migration.
UPDATE catalog.products SET image_url = '/images/products/' || slug || '.jpg'
WHERE slug IN (
    'aashirvaad-whole-wheat-atta',
    'fortune-chakki-fresh-atta',
    'besan-gram-flour',
    'maida-refined-flour',
    'india-gate-basmati-rice',
    'sona-masoori-rice',
    'toor-dal-arhar',
    'moong-dal',
    'chana-dal',
    'fortune-sunflower-oil',
    'saffola-gold-oil',
    'amul-pure-ghee',
    'everest-turmeric-powder',
    'mdh-red-chilli-powder',
    'coriander-powder',
    'garam-masala',
    'amul-gold-milk',
    'amul-butter',
    'nestle-dahi-curd',
    'amul-cheese-slices',
    'amul-paneer',
    'parle-g-biscuits',
    'britannia-good-day-cashew',
    'lays-classic-salted-chips',
    'haldiram-aloo-bhujia',
    'tata-tea-premium',
    'bru-instant-coffee',
    'bournvita-health-drink',
    'real-mixed-fruit-juice',
    'surf-excel-detergent-powder',
    'vim-dishwash-bar',
    'harpic-toilet-cleaner',
    'lizol-floor-cleaner'
);
