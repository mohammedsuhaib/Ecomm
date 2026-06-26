package com.townbasket.catalog;

/**
 * Admin request to create a category. Plain record — validation lives in the
 * service (it throws {@code BusinessRuleException} for rule violations), not in
 * bean-validation annotations.
 *
 * @param name      required, non-blank
 * @param slug      optional — auto-generated from {@code name} when blank
 * @param sortOrder optional — defaults to {@code max(sortOrder)+10} (or 0 if none)
 * @param imageUrl  optional
 */
public record CreateCategoryRequest(
        String name,
        String slug,
        Integer sortOrder,
        String imageUrl) {
}
