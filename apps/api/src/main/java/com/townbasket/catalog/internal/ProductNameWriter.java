package com.townbasket.catalog.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a transliterated Kannada name onto a single product in its own short
 * transaction. Kept separate from the backfill job so the (potentially slow)
 * transliteration HTTP calls happen OUTSIDE any database transaction — each save
 * here loads and updates one row and commits immediately.
 */
@Component
class ProductNameWriter {

    private final ProductRepository products;

    ProductNameWriter(ProductRepository products) {
        this.products = products;
    }

    @Transactional
    void save(Long productId, String nameKn) {
        products.findById(productId).ifPresent(p -> p.setNameKn(nameKn));
    }
}
