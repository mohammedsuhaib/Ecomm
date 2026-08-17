-- GST fields for tax calculation (tax module).
--
-- Selling prices are MRP-style TAX-INCLUSIVE, so adding a rate never changes
-- what the customer pays — it only lets orders/invoices extract the taxable
-- value + CGST/SGST split. Slabs follow the structure in force since the
-- September 2025 rationalisation: 0, 5, 18, 40.
--
-- hsn_code is the HSN classification printed on GST invoices. Seeds leave it
-- NULL (a wrong HSN is worse than an absent one) — staff fill it per product.

ALTER TABLE catalog.products
    ADD COLUMN hsn_code TEXT,
    ADD COLUMN gst_rate NUMERIC(4,2) NOT NULL DEFAULT 0
        CONSTRAINT chk_products_gst_rate CHECK (gst_rate IN (0, 5, 18, 40));

-- Approximate slab per seeded category (packaged/branded goods). These are
-- dev-seed defaults — the store owner must verify each product's actual rate
-- and HSN in the admin panel before invoices go to real customers.
UPDATE catalog.products p SET gst_rate = 5
FROM catalog.categories c
WHERE p.category_id = c.id
  AND c.slug IN ('atta-flours', 'rice-dals', 'edible-oils-ghee',
                 'spices-masalas', 'biscuits-snacks', 'beverages');

-- Fresh dairy (milk, curd) is NIL-rated; ghee sits in edible-oils-ghee above.
UPDATE catalog.products p SET gst_rate = 0
FROM catalog.categories c
WHERE p.category_id = c.id AND c.slug = 'dairy';

UPDATE catalog.products p SET gst_rate = 18
FROM catalog.categories c
WHERE p.category_id = c.id AND c.slug = 'cleaning-household';
