package com.townbasket.catalog;

import java.util.List;

/**
 * Admin-facing product representation returned by the staff-secured
 * {@code /api/v1/admin/catalog/**} surface. Carries its variants inline as
 * {@link AdminVariantDto} (which DO include cost price) and resolves the owning
 * category's name for display.
 */
public record AdminProductDto(
        Long id,
        String name,
        String nameKn,
        String slug,
        Long categoryId,
        String categoryName,
        String description,
        boolean vegMarker,
        String imageUrl,
        boolean available,
        boolean featured,
        List<AdminVariantDto> variants) {
}
