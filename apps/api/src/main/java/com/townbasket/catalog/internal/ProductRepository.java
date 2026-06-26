package com.townbasket.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for products. */
interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Page<ProductEntity> findByCategoryId(Long categoryId, Pageable pageable);

    Page<ProductEntity> findByFeaturedTrue(Pageable pageable);

    Page<ProductEntity> findByCategoryIdAndFeaturedTrue(Long categoryId, Pageable pageable);

    Optional<ProductEntity> findBySlug(String slug);

    /** Whether any product references a category — the category-delete guard. */
    boolean existsByCategoryId(Long categoryId);

    /** Admin name search (case-insensitive contains), optionally scoped to a category. */
    Page<ProductEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<ProductEntity> findByCategoryIdAndNameContainingIgnoreCase(
            Long categoryId, String name, Pageable pageable);

    /** Products still missing a Kannada name — drained by the transliteration backfill. */
    List<ProductEntity> findByNameKnIsNull(Pageable pageable);

    /**
     * Full-text + trigram search over product name/description.
     *
     * <p>Combines Postgres full-text matching (websearch_to_tsquery against the
     * trigger-maintained {@code search_vector}) with a pg_trgm similarity match
     * on the name, so typos ("biscit" -> "biscuit") still surface results.
     * Ordered by full-text rank, then trigram similarity.
     */
    @Query(value = """
            SELECT * FROM catalog.products p
            WHERE p.search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, p.name) >= 0.3
            ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('simple', :q)) DESC,
                     word_similarity(:q, p.name) DESC,
                     p.name ASC
            """,
            countQuery = """
            SELECT count(*) FROM catalog.products p
            WHERE p.search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, p.name) >= 0.3
            """,
            nativeQuery = true)
    Page<ProductEntity> search(@Param("q") String q, Pageable pageable);
}
