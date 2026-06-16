package com.townbasket.catalog;

/**
 * Public category representation returned by the catalog API.
 *
 * <p>Part of the catalog module's published API (base package). Carries no JPA
 * types so entities never leak across module boundaries.
 */
public record CategoryDto(
        Long id,
        String name,
        String slug,
        String imageUrl,
        Integer sortOrder) {
}
