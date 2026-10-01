package com.townbasket.catalog.internal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a transliterated Kannada name onto a single product or category in its own short
 * transaction. Kept separate from the backfill job so the (potentially slow)
 * transliteration HTTP calls happen OUTSIDE any database transaction — each save
 * here loads and updates one row and commits immediately.
 */
@Component
class ProductNameWriter {

    private final ProductRepository products;
    private final CategoryRepository categories;

    ProductNameWriter(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional
    void save(Long productId, String nameKn) {
        products.findById(productId).ifPresent(p -> p.setNameKn(nameKn));
    }

    /**
     * Evicts the public category list, which is cached and would otherwise keep
     * the blank name. Public because Spring's cache interception only applies to
     * public methods by default.
     */
    @Transactional
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public void saveCategory(Long categoryId, String nameKn) {
        categories.findById(categoryId).ifPresent(c -> c.setNameKn(nameKn));
    }
}
