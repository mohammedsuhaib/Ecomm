package com.townbasket.catalog;

/**
 * Admin request to update a category. The slug is immutable (not editable), so it
 * is intentionally absent. Plain record — validation lives in the service.
 *
 * @param name      required, non-blank
 * @param sortOrder optional — when {@code null}, the existing value is kept
 * @param imageUrl  optional — when {@code null}, the existing value is kept
 */
public record UpdateCategoryRequest(
        String name,
        Integer sortOrder,
        String imageUrl) {
}
