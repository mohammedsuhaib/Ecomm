-- Seed the single operating store. Idempotent: guarded on name uniqueness so a
-- re-apply will not create a duplicate store row.
--
-- NOTE: lat/lng below (12.21, 76.89) are APPROXIMATE coordinates for the
-- Gargeshwari / T. Narasipura area. The client will confirm the exact storefront
-- coordinates; update this seed (or the store row via admin) once confirmed.

INSERT INTO serviceability.stores
    (name, address, lat, lng, delivery_radius_m, opening_time, closing_time, min_order_value, active)
SELECT
    'Town Basket',
    'No. 40, Sultania Hosa Colony, T. Narasipura Taluk, Gargeshwari, Mysuru, Karnataka 571110',
    12.21,   -- approximate latitude  (client to confirm exact coords)
    76.89,   -- approximate longitude (client to confirm exact coords)
    5000,
    TIME '08:00',
    TIME '21:00',
    499,
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM serviceability.stores WHERE name = 'Town Basket'
);
