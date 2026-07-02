-- identity fix: correct the dev DELIVERY_AGENT password hash.
--
-- V2_3 seeded delivery@townbasket.local with a BCrypt hash that did NOT match
-- the documented dev password (Delivery@12345), so staff/login returned 401.
-- This forward migration replaces the hash with a verified-correct one rather
-- than editing V2_3 in place (editing an already-applied migration would trip
-- Flyway's checksum validation and fail boot).
--
-- Password (BCrypt cost 10): delivery@townbasket.local / Delivery@12345
-- Idempotent: only touches the dev seed row, safe to re-run.

UPDATE identity.users
   SET password_hash = '$2a$10$hqGt3cS0GvogudEDATqCGOopckr0vWv07xteZpT9NG8kyjIfPsaPW'
 WHERE email = 'delivery@townbasket.local';
