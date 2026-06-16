-- catalog module tables (schema `catalog`).
-- Fixed-price PACKAGED goods only (dry groceries + dairy); no loose/weight items.
-- Owns: categories, products, product_variants.
--
-- IMPORTANT: product_variants.cost_price is INTERNAL ONLY. It feeds gross-profit
-- reporting and must NEVER be returned in any public API response.

-- Trigram extension for typo-tolerant search on product name.
-- Installed into `public` explicitly: this migration runs with Flyway's
-- default schema set to `flyway`, but the app queries with `public` on its
-- search_path, so the trigram operators (<%, word_similarity, gin_trgm_ops)
-- must live in `public` to be resolvable at runtime.
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;

CREATE TABLE catalog.categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL UNIQUE,
    sort_order INT         NOT NULL DEFAULT 0,
    image_url  TEXT
);

CREATE TABLE catalog.products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT      NOT NULL REFERENCES catalog.categories (id),
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL UNIQUE,
    description TEXT,
    veg_marker  BOOLEAN     NOT NULL DEFAULT TRUE,
    image_url   TEXT,
    available   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Full-text search vector over name (weight A) + description (weight B).
    -- Maintained by a trigger so application code never has to set it.
    search_vector tsvector
);

CREATE TABLE catalog.product_variants (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    BIGINT        NOT NULL REFERENCES catalog.products (id),
    label         TEXT          NOT NULL,            -- e.g. '1 kg', '500 g', '1 L'
    selling_price NUMERIC(10,2) NOT NULL,
    cost_price    NUMERIC(10,2) NOT NULL,            -- INTERNAL ONLY — never exposed via API
    mrp           NUMERIC(10,2),                     -- nullable
    available     BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order    INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_products_category_id ON catalog.products (category_id);
CREATE INDEX idx_product_variants_product_id ON catalog.product_variants (product_id);

-- GIN index for full-text search.
CREATE INDEX idx_products_search_vector ON catalog.products USING GIN (search_vector);

-- GIN trigram index for typo-tolerant fuzzy matching on the product name.
-- Operator class qualified with `public` since pg_trgm lives there (see above)
-- and `public` is not on Flyway's migration-time search_path.
CREATE INDEX idx_products_name_trgm ON catalog.products USING GIN (name public.gin_trgm_ops);

-- Keep search_vector in sync on insert/update.
CREATE OR REPLACE FUNCTION catalog.products_search_vector_update()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_search_vector
    BEFORE INSERT OR UPDATE OF name, description
    ON catalog.products
    FOR EACH ROW
    EXECUTE FUNCTION catalog.products_search_vector_update();
