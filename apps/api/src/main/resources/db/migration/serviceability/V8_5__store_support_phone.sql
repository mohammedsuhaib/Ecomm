-- The store's public contact number.
--
-- The storefront tells a customer to "contact support" when a cancellation
-- fails or its window has closed, but nothing in the app carried a number to
-- call — the advice was a dead end. The number belongs beside the store's other
-- public details (name, address, hours) so staff can change it from the admin
-- store card rather than needing a deploy.
--
-- Nullable on purpose: until someone fills it in, the storefront says nothing
-- about contacting the store rather than promising a channel that isn't there.

ALTER TABLE serviceability.stores
    ADD COLUMN IF NOT EXISTS support_phone TEXT;
