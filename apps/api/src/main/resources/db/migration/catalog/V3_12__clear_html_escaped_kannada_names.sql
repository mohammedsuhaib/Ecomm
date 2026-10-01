-- Google Input Tools returns HTML-escaped text ("&" as "&amp;", "'" as "&#39;"),
-- and earlier builds stored it verbatim, so the storefront showed the entity
-- itself. Clear only those values; the transliteration backfill refills them
-- with the now-unescaped result. Hand-written names never contain an entity.
UPDATE catalog.products
   SET name_kn = NULL
 WHERE name_kn ~ '&(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);';

UPDATE catalog.categories
   SET name_kn = NULL
 WHERE name_kn ~ '&(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);';
