-- Rider self-service availability. `active` is the ADMIN's switch (account
-- enabled at all); `on_duty` is the RIDER's (taking jobs right now). Both must
-- be true for a new assignment. Defaults TRUE so existing riders keep working
-- exactly as before until they first toggle themselves off.
ALTER TABLE identity.users
    ADD COLUMN on_duty BOOLEAN NOT NULL DEFAULT TRUE;
