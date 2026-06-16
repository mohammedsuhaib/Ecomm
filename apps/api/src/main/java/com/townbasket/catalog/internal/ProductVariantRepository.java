package com.townbasket.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal Spring Data repository for product variants. Supports the
 * cross-module variant lookups exposed by the catalog public API
 * ({@code findVariant} / {@code costPrice}).
 */
interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {
}
