-- identity seed: dev-only ADMIN and STORE_STAFF accounts (M4_CONTRACT §8).
-- Idempotent (guarded on email) so re-running migrations / an upgraded DB is safe.
--
-- Passwords are BCrypt (cost 10):
--   admin@townbasket.local / Admin@12345
--   staff@townbasket.local / Staff@12345
-- Plaintext is documented for local dev ONLY; these are not production secrets.
-- `role` values are the Role enum names (ADMIN, STORE_STAFF).

INSERT INTO identity.users (role, email, password_hash, name, active)
SELECT 'ADMIN', 'admin@townbasket.local',
       '$2a$10$5jDBACSHNvLBpy3TmFN3XOVCFN6/3ucnu70XDNWxVjg8vXJMLesEK',
       'Town Basket Admin', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM identity.users WHERE email = 'admin@townbasket.local'
);

INSERT INTO identity.users (role, email, password_hash, name, active)
SELECT 'STORE_STAFF', 'staff@townbasket.local',
       '$2a$10$Y2xVPFPtzeF4b881PmbHjeIHF6i3uK6SmUU0FMMC7WMrt6Bl4dklO',
       'Town Basket Staff', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM identity.users WHERE email = 'staff@townbasket.local'
);
