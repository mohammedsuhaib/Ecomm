-- B3: lower the Town Basket minimum order value from ₹499 to ₹299 (M4 decision).
-- Idempotent: re-running simply re-sets the same value on the single store row.

UPDATE serviceability.stores
SET min_order_value = 299
WHERE name = 'Town Basket';
