package com.townbasket.catalog;

import java.util.List;

/**
 * Admin request to create a product (optionally with variants). Plain record —
 * validation lives in the service.
 *
 * @param name        required, non-blank
 * @param nameKn      optional Kannada name — when blank and transliteration is
 *                    enabled, it is best-effort auto-filled from {@code name}
 * @param slug        optional — auto-generated from {@code name} when blank
 * @param categoryId  required, must reference an existing category
 * @param description optional
 * @param vegMarker   optional — defaults to {@code true}
 * @param imageUrl    optional
 * @param available   optional — defaults to {@code true}
 * @param featured    optional — defaults to {@code false}
 * @param variants    optional — initial variants, each validated like a create
 */
public record CreateProductRequest(
        String name,
        String nameKn,
        String slug,
        Long categoryId,
        String description,
        Boolean vegMarker,
        String imageUrl,
        Boolean available,
        Boolean featured,
        List<CreateVariantRequest> variants) {
}
