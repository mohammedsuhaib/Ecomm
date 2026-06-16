/**
 * {@code catalog} module — categories, products, variants, images and pricing.
 *
 * <p>Each variant carries a manually-maintained selling price and cost price.
 * Search uses Postgres full-text ({@code tsvector} + trigram). Read-heavy and
 * cache-friendly.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Catalog")
package com.townbasket.catalog;
