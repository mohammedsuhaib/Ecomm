-- identity seed: dev-only DELIVERY_AGENT account.
-- Idempotent (guarded on email) so re-running migrations is safe.
--
-- Password (BCrypt cost 10): delivery@townbasket.local / Delivery@12345
-- Plaintext is documented for local dev ONLY; not a production secret.
-- In production, create delivery agents via admin tools or INSERT with bcrypt hash.

INSERT INTO identity.users (role, email, password_hash, name, active)
SELECT 'DELIVERY_AGENT', 'delivery@townbasket.local',
       '$2a$10$4OfJO1vNEkPAV76zz2LUueSf7AEJWOqTr/cY0GEF.H/P6tl5P.KAa',
       'Town Basket Delivery', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM identity.users WHERE email = 'delivery@townbasket.local'
);
