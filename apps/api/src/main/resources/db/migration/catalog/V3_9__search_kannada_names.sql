-- Make the Kannada product name searchable.
--
-- THE BUG. Customers browsing in Kannada saw Kannada product names (name_kn,
-- filled in by the transliteration backfill) but could not search for them:
-- every search path looked only at the English `name`. search_vector was built
-- from name + description, and the fuzzy fallback compared the query against
-- `name` alone, so a Kannada term matched nothing at all — not even the product
-- whose Kannada name was showing on screen.
--
-- TWO SEPARATE CAUSES, both fixed here:
--
--  1. name_kn was not in the vector. Fixed by adding it at weight A alongside
--     the English name — a name is a name, whichever script it is written in.
--
--  2. The trigger fired on `UPDATE OF name, description` only. name_kn is
--     written almost exclusively by the backfill job, which touches nothing
--     else, so even after adding it to the vector the job's writes would not
--     have rebuilt it and the Kannada name would have stayed unsearchable
--     until the next unrelated edit. name_kn joins the column list.
--
-- 'simple' (not 'english') stays: it is the configuration the existing vector
-- uses, it applies no language-specific stemming or stop-word list, and that is
-- what makes it correct for mixed Kannada/English text. An 'english' dictionary
-- would stem Kannada tokens by English rules.

CREATE OR REPLACE FUNCTION catalog.products_search_vector_update()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.name_kn, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_products_search_vector ON catalog.products;

CREATE TRIGGER trg_products_search_vector
    BEFORE INSERT OR UPDATE OF name, name_kn, description
    ON catalog.products
    FOR EACH ROW
    EXECUTE FUNCTION catalog.products_search_vector_update();

-- Rebuild every existing row: the rows already in the table were vectorised by
-- the old function and would otherwise stay unsearchable in Kannada until each
-- was next edited. A no-op UPDATE fires the trigger above.
UPDATE catalog.products SET name_kn = name_kn;

-- Trigram index on the Kannada name, mirroring idx_products_name_trgm on the
-- English one, so the typo-tolerant `word_similarity` fallback the search query
-- applies to name_kn can use an index instead of scanning every product.
-- Operator class qualified with `public` — pg_trgm lives there and `public` is
-- not on Flyway's migration-time search_path (same reason as V3_1).
CREATE INDEX IF NOT EXISTS idx_products_name_kn_trgm
    ON catalog.products USING GIN (name_kn public.gin_trgm_ops);
