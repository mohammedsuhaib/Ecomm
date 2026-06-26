-- M4: associate a cart with a user (anonymous-cart merge on login). Nullable:
-- guest carts keep user_id NULL until claimed/merged. The identity user id is a
-- plain id (no cross-schema FK — schema-per-module rule).
ALTER TABLE cart.carts ADD COLUMN user_id BIGINT;
CREATE INDEX idx_carts_user_id ON cart.carts (user_id);
