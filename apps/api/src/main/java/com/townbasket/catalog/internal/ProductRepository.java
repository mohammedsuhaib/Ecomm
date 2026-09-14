package com.townbasket.catalog.internal;

import java.math.BigDecimal;
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

    /** GST rates already in use for an HSN code, most common first. */
    @Query("""
            SELECT p.gstRate FROM ProductEntity p
            WHERE p.hsnCode = :hsnCode
            GROUP BY p.gstRate
            ORDER BY COUNT(p) DESC
            """)
    List<BigDecimal> gstRatesForHsn(String hsnCode);

    Optional<ProductEntity> findBySlug(String slug);

    /** Whether any product references a category — the category-delete guard. */
    boolean existsByCategoryId(Long categoryId);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Admin name search (case-insensitive contains), optionally scoped to a category.
     *
     * <p>Raw {@code ILIKE} rather than Spring Data's {@code ...NameContainingIgnoreCase}
     * derivation on purpose: that derivation generates {@code upper(name) LIKE upper(?)},
     * and an expression on the left-hand side makes the GIN trigram index on
     * {@code products(name)} unusable. {@code name ILIKE '%term%'} is exactly the
     * leading-wildcard shape {@code gin_trgm_ops} exists to serve, so this form can
     * use the index the catalog migration already created.
     */
    @Query("""
            SELECT p FROM ProductEntity p
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))
            """)
    Page<ProductEntity> searchByName(@Param("name") String name, Pageable pageable);

    @Query("""
            SELECT p FROM ProductEntity p
            WHERE p.categoryId = :categoryId
              AND LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))
            """)
    Page<ProductEntity> searchByNameInCategory(
            @Param("categoryId") Long categoryId, @Param("name") String name, Pageable pageable);

    /**
     * Sorted product listing, ordered and paged entirely in SQL.
     *
     * <p>Replaces an in-memory sort that loaded a capped 1&nbsp;000 rows, sorted them
     * in Java and reported {@code totalElements} as the size of that slice — so past
     * 1&nbsp;000 products a sorted listing both under-reported the catalogue and made
     * the remainder unreachable, and each of those rows lazy-loaded its variants.
     *
     * <p>The price and discount keys span variants, which is what made a plain
     * {@code ORDER BY} awkward; a grouped sub-select supplies them per product:
     * <ul>
     *   <li>{@code min_price} — lowest selling price among AVAILABLE variants, NULL
     *       when a product has none, so those sort last in both directions rather
     *       than leading "price: high to low" with things nobody can buy.</li>
     *   <li>{@code max_discount} — largest {@code mrp - selling_price}; a null
     *       {@code mrp} counts as zero discount, matching the previous behaviour.</li>
     * </ul>
     *
     * <p>Only the {@code CASE} arm matching {@code :sort} yields a value; the others
     * are NULL for every row and so contribute no ordering. {@code p.id} breaks ties
     * last, which is what keeps paging stable across requests.
     */
    @Query(value = """
            SELECT p.* FROM catalog.products p
            LEFT JOIN (
                SELECT v.product_id,
                       MIN(CASE WHEN v.available THEN v.selling_price END)     AS min_price,
                       MAX(COALESCE(v.mrp, v.selling_price) - v.selling_price) AS max_discount
                FROM catalog.product_variants v
                GROUP BY v.product_id
            ) agg ON agg.product_id = p.id
            WHERE (CAST(:categoryId AS bigint) IS NULL OR p.category_id = CAST(:categoryId AS bigint))
              AND (:featuredOnly = FALSE OR p.featured = TRUE)
            ORDER BY
              CASE WHEN :sort = 'PRICE_ASC'  THEN agg.min_price    END ASC  NULLS LAST,
              CASE WHEN :sort = 'PRICE_DESC' THEN agg.min_price    END DESC NULLS LAST,
              CASE WHEN :sort = 'DISCOUNT'   THEN agg.max_discount END DESC NULLS LAST,
              CASE WHEN :sort = 'NAME'       THEN LOWER(p.name)    END ASC  NULLS LAST,
              CASE WHEN :sort = 'NAME_DESC'  THEN LOWER(p.name)    END DESC NULLS LAST,
              p.id ASC
            """,
            countQuery = """
            SELECT count(*) FROM catalog.products p
            WHERE (CAST(:categoryId AS bigint) IS NULL OR p.category_id = CAST(:categoryId AS bigint))
              AND (:featuredOnly = FALSE OR p.featured = TRUE)
            """,
            nativeQuery = true)
    Page<ProductEntity> findSorted(@Param("categoryId") Long categoryId,
                                   @Param("featuredOnly") boolean featuredOnly,
                                   @Param("sort") String sort,
                                   Pageable pageable);

    /**
     * Sorted keyword search — the same SQL ordering as {@link #findSorted}, applied to
     * the relevance match set instead of the whole catalogue, and likewise paged in
     * SQL rather than truncated. The match predicate is kept identical to
     * {@link #search} so re-sorting never changes WHICH products a query finds, only
     * their order.
     */
    @Query(value = """
            SELECT p.* FROM catalog.products p
            LEFT JOIN (
                SELECT v.product_id,
                       MIN(CASE WHEN v.available THEN v.selling_price END)     AS min_price,
                       MAX(COALESCE(v.mrp, v.selling_price) - v.selling_price) AS max_discount
                FROM catalog.product_variants v
                GROUP BY v.product_id
            ) agg ON agg.product_id = p.id
            WHERE p.search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, p.name) >= 0.3
            ORDER BY
              CASE WHEN :sort = 'PRICE_ASC'  THEN agg.min_price    END ASC  NULLS LAST,
              CASE WHEN :sort = 'PRICE_DESC' THEN agg.min_price    END DESC NULLS LAST,
              CASE WHEN :sort = 'DISCOUNT'   THEN agg.max_discount END DESC NULLS LAST,
              CASE WHEN :sort = 'NAME'       THEN LOWER(p.name)    END ASC  NULLS LAST,
              CASE WHEN :sort = 'NAME_DESC'  THEN LOWER(p.name)    END DESC NULLS LAST,
              p.id ASC
            """,
            countQuery = """
            SELECT count(*) FROM catalog.products p
            WHERE p.search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, p.name) >= 0.3
            """,
            nativeQuery = true)
    Page<ProductEntity> searchSorted(@Param("q") String q,
                                     @Param("sort") String sort,
                                     Pageable pageable);

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
