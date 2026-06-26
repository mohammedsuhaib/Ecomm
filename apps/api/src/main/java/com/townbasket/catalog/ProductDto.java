package com.townbasket.catalog;

import java.util.List;

/**
 * Public product representation returned by the catalog API. Carries the
 * product's variants inline. No cost price anywhere in this graph.
 */
public record ProductDto(
        Long id,
        String name,
        String nameKn,
        String slug,
        Long categoryId,
        String description,
        boolean vegMarker,
        String imageUrl,
        boolean available,
        boolean featured,
        List<ProductVariantDto> variants) {
}
