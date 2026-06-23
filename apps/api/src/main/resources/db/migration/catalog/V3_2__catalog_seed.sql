-- Dev/demo seed data for the catalog (dry groceries + dairy only).
-- Idempotent: each INSERT guards on slug uniqueness via ON CONFLICT DO NOTHING,
-- so re-running (or a baseline re-apply) cannot create duplicates.
--
-- Prices are in INR. cost_price is internal-only (gross-profit reporting).

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO catalog.categories (name, slug, sort_order, image_url) VALUES
    ('Atta & Flours',        'atta-flours',        10, NULL),
    ('Rice & Dals',          'rice-dals',          20, NULL),
    ('Edible Oils & Ghee',   'edible-oils-ghee',   30, NULL),
    ('Spices & Masalas',     'spices-masalas',     40, NULL),
    ('Dairy',                'dairy',              50, NULL),
    ('Biscuits & Snacks',    'biscuits-snacks',    60, NULL),
    ('Beverages',            'beverages',          70, NULL),
    ('Cleaning & Household', 'cleaning-household', 80, NULL)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Products + variants
-- Helper pattern: insert product referencing category by slug, then variants
-- referencing product by slug. ON CONFLICT keeps re-runs idempotent.
-- ---------------------------------------------------------------------------

-- Atta & Flours -------------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Aashirvaad Whole Wheat Atta', 'aashirvaad-whole-wheat-atta',
       'Whole wheat chakki atta, soft rotis.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'atta-flours'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Fortune Chakki Fresh Atta', 'fortune-chakki-fresh-atta',
       '100% whole wheat atta.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'atta-flours'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Besan (Gram Flour)', 'besan-gram-flour',
       'Fine gram flour for pakoras and sweets.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'atta-flours'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Maida (Refined Flour)', 'maida-refined-flour',
       'Refined wheat flour for baking.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'atta-flours'
ON CONFLICT (slug) DO NOTHING;

-- Rice & Dals ---------------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'India Gate Basmati Rice', 'india-gate-basmati-rice',
       'Long-grain aged basmati rice.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'rice-dals'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Sona Masoori Rice', 'sona-masoori-rice',
       'Premium everyday Sona Masoori rice.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'rice-dals'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Toor Dal (Arhar)', 'toor-dal-arhar',
       'Polished split pigeon peas.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'rice-dals'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Moong Dal', 'moong-dal',
       'Split yellow moong dal.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'rice-dals'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Chana Dal', 'chana-dal',
       'Split Bengal gram.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'rice-dals'
ON CONFLICT (slug) DO NOTHING;

-- Edible Oils & Ghee --------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Fortune Sunflower Oil', 'fortune-sunflower-oil',
       'Refined sunflower cooking oil.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'edible-oils-ghee'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Saffola Gold Oil', 'saffola-gold-oil',
       'Blended cooking oil for a healthy heart.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'edible-oils-ghee'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Amul Pure Ghee', 'amul-pure-ghee',
       'Pure cow ghee, rich aroma.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'edible-oils-ghee'
ON CONFLICT (slug) DO NOTHING;

-- Spices & Masalas ----------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Everest Turmeric Powder', 'everest-turmeric-powder',
       'Pure haldi powder.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'spices-masalas'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'MDH Red Chilli Powder', 'mdh-red-chilli-powder',
       'Spicy red chilli powder.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'spices-masalas'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Coriander Powder', 'coriander-powder',
       'Ground dhania powder.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'spices-masalas'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Garam Masala', 'garam-masala',
       'Aromatic blend of whole spices.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'spices-masalas'
ON CONFLICT (slug) DO NOTHING;

-- Dairy ---------------------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Amul Gold Milk', 'amul-gold-milk',
       'Full-cream homogenised toned milk.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Amul Butter', 'amul-butter',
       'Pasteurised table butter.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Nestle Dahi (Curd)', 'nestle-dahi-curd',
       'Fresh thick curd.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

-- image_url is set here (most products leave it NULL for now). Served from the
-- storefront's /public for local/dev; swap to the DO Spaces URL in production.
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, image_url, available)
SELECT id, 'Amul Dahi', 'amul-dahi',
       'Toned milk dahi — creamy and tasty.', TRUE,
       '/products/amul-dahi.jpg', TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Amul Cheese Slices', 'amul-cheese-slices',
       'Processed cheese slices.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Amul Paneer', 'amul-paneer',
       'Fresh malai paneer block.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'dairy'
ON CONFLICT (slug) DO NOTHING;

-- Biscuits & Snacks ---------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Parle-G Biscuits', 'parle-g-biscuits',
       'Classic glucose biscuits.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'biscuits-snacks'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Britannia Good Day Cashew', 'britannia-good-day-cashew',
       'Cashew cookies.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'biscuits-snacks'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Lays Classic Salted Chips', 'lays-classic-salted-chips',
       'Crispy potato chips.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'biscuits-snacks'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Haldiram Aloo Bhujia', 'haldiram-aloo-bhujia',
       'Spicy potato sev.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'biscuits-snacks'
ON CONFLICT (slug) DO NOTHING;

-- Beverages -----------------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Tata Tea Premium', 'tata-tea-premium',
       'Strong, rich tea blend.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'beverages'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Bru Instant Coffee', 'bru-instant-coffee',
       'Instant coffee granules.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'beverages'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Bournvita Health Drink', 'bournvita-health-drink',
       'Chocolate malt health drink.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'beverages'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Real Mixed Fruit Juice', 'real-mixed-fruit-juice',
       'Mixed fruit nectar.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'beverages'
ON CONFLICT (slug) DO NOTHING;

-- Cleaning & Household ------------------------------------------------------
INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Surf Excel Detergent Powder', 'surf-excel-detergent-powder',
       'Tough on stains detergent powder.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'cleaning-household'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Vim Dishwash Bar', 'vim-dishwash-bar',
       'Lemon dishwash bar.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'cleaning-household'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Harpic Toilet Cleaner', 'harpic-toilet-cleaner',
       'Disinfectant toilet cleaner.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'cleaning-household'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog.products (category_id, name, slug, description, veg_marker, available)
SELECT id, 'Lizol Floor Cleaner', 'lizol-floor-cleaner',
       'Disinfectant surface cleaner.', TRUE, TRUE
FROM catalog.categories WHERE slug = 'cleaning-household'
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Variants (selling_price, cost_price [internal], mrp).
-- Insert only if the (product, label) pair does not yet exist.
-- ---------------------------------------------------------------------------
INSERT INTO catalog.product_variants (product_id, label, selling_price, cost_price, mrp, available, sort_order)
SELECT p.id, v.label, v.selling_price, v.cost_price, v.mrp, TRUE, v.sort_order
FROM (VALUES
    -- product_slug, label, selling, cost, mrp, sort
    ('aashirvaad-whole-wheat-atta', '5 kg',   265.00, 230.00, 285.00, 1),
    ('aashirvaad-whole-wheat-atta', '10 kg',  515.00, 455.00, 545.00, 2),
    ('fortune-chakki-fresh-atta',   '5 kg',   249.00, 215.00, 270.00, 1),
    ('fortune-chakki-fresh-atta',   '10 kg',  489.00, 430.00, 520.00, 2),
    ('besan-gram-flour',            '500 g',   62.00,  50.00,  70.00, 1),
    ('besan-gram-flour',            '1 kg',   118.00,  96.00, 130.00, 2),
    ('maida-refined-flour',         '500 g',   34.00,  27.00,  40.00, 1),
    ('maida-refined-flour',         '1 kg',    62.00,  50.00,  72.00, 2),

    ('india-gate-basmati-rice',     '1 kg',   135.00, 110.00, 150.00, 1),
    ('india-gate-basmati-rice',     '5 kg',   640.00, 540.00, 695.00, 2),
    ('sona-masoori-rice',           '5 kg',   345.00, 300.00, 375.00, 1),
    ('sona-masoori-rice',           '25 kg', 1640.00,1450.00,1750.00, 2),
    ('toor-dal-arhar',              '500 g',   92.00,  78.00, 100.00, 1),
    ('toor-dal-arhar',              '1 kg',   178.00, 152.00, 195.00, 2),
    ('moong-dal',                   '500 g',   88.00,  74.00,  98.00, 1),
    ('moong-dal',                   '1 kg',   170.00, 145.00, 188.00, 2),
    ('chana-dal',                   '500 g',   54.00,  44.00,  62.00, 1),
    ('chana-dal',                   '1 kg',   102.00,  85.00, 115.00, 2),

    ('fortune-sunflower-oil',       '1 L',    135.00, 118.00, 150.00, 1),
    ('fortune-sunflower-oil',       '5 L',    660.00, 590.00, 710.00, 2),
    ('saffola-gold-oil',            '1 L',    175.00, 155.00, 195.00, 1),
    ('amul-pure-ghee',              '500 ml', 320.00, 285.00, 345.00, 1),
    ('amul-pure-ghee',              '1 L',    625.00, 560.00, 670.00, 2),

    ('everest-turmeric-powder',     '100 g',   38.00,  30.00,  44.00, 1),
    ('everest-turmeric-powder',     '200 g',   72.00,  58.00,  82.00, 2),
    ('mdh-red-chilli-powder',       '100 g',   58.00,  47.00,  66.00, 1),
    ('mdh-red-chilli-powder',       '200 g',  110.00,  90.00, 125.00, 2),
    ('coriander-powder',            '100 g',   42.00,  33.00,  50.00, 1),
    ('coriander-powder',            '200 g',   80.00,  64.00,  92.00, 2),
    ('garam-masala',                '100 g',   85.00,  70.00,  95.00, 1),

    ('amul-gold-milk',              '500 ml',  35.00,  31.00,  35.00, 1),
    ('amul-gold-milk',              '1 L',     70.00,  62.00,  70.00, 2),
    ('amul-butter',                 '100 g',   58.00,  50.00,  60.00, 1),
    ('amul-butter',                 '500 g',  275.00, 245.00, 285.00, 2),
    ('nestle-dahi-curd',            '400 g',   42.00,  35.00,  48.00, 1),
    ('amul-dahi',                   '200 g',   25.00,  20.00,  28.00, 1),
    ('amul-dahi',                   '400 g',   45.00,  38.00,  50.00, 2),
    ('amul-cheese-slices',          '100 g',   90.00,  77.00, 100.00, 1),
    ('amul-cheese-slices',          '200 g',  165.00, 142.00, 185.00, 2),
    ('amul-paneer',                 '200 g',   95.00,  82.00, 105.00, 1),
    ('amul-paneer',                 '500 g',  225.00, 198.00, 245.00, 2),

    ('parle-g-biscuits',            '250 g',   25.00,  20.00,  28.00, 1),
    ('parle-g-biscuits',            '800 g',   90.00,  76.00, 100.00, 2),
    ('britannia-good-day-cashew',   '200 g',   45.00,  37.00,  50.00, 1),
    ('lays-classic-salted-chips',   '52 g',    20.00,  16.00,  20.00, 1),
    ('lays-classic-salted-chips',   '90 g',    45.00,  37.00,  50.00, 2),
    ('haldiram-aloo-bhujia',        '200 g',   55.00,  45.00,  60.00, 1),
    ('haldiram-aloo-bhujia',        '400 g',  100.00,  84.00, 110.00, 2),

    ('tata-tea-premium',            '250 g',  140.00, 120.00, 155.00, 1),
    ('tata-tea-premium',            '500 g',  270.00, 235.00, 295.00, 2),
    ('bru-instant-coffee',          '50 g',   165.00, 142.00, 180.00, 1),
    ('bru-instant-coffee',          '100 g',  315.00, 275.00, 340.00, 2),
    ('bournvita-health-drink',      '500 g',  235.00, 205.00, 255.00, 1),
    ('real-mixed-fruit-juice',      '1 L',    115.00,  98.00, 125.00, 1),

    ('surf-excel-detergent-powder', '1 kg',   140.00, 120.00, 155.00, 1),
    ('surf-excel-detergent-powder', '4 kg',   525.00, 465.00, 565.00, 2),
    ('vim-dishwash-bar',            '300 g',   30.00,  24.00,  35.00, 1),
    ('harpic-toilet-cleaner',       '500 ml',  90.00,  76.00, 100.00, 1),
    ('harpic-toilet-cleaner',       '1 L',    165.00, 142.00, 180.00, 2),
    ('lizol-floor-cleaner',         '500 ml', 105.00,  90.00, 115.00, 1),
    ('lizol-floor-cleaner',         '975 ml', 185.00, 162.00, 200.00, 2)
) AS v(product_slug, label, selling_price, cost_price, mrp, sort_order)
JOIN catalog.products p ON p.slug = v.product_slug
WHERE NOT EXISTS (
    SELECT 1 FROM catalog.product_variants pv
    WHERE pv.product_id = p.id AND pv.label = v.label
);
