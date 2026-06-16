package com.townbasket.catalog;

import org.springframework.context.annotation.Configuration;

/**
 * Module configuration anchor for {@code catalog}. Entities, repositories and
 * the {@link CatalogService} implementation live in the non-exported
 * {@code internal} sub-package; only DTOs, {@link CatalogService} and the
 * controller form the module's public API.
 */
@Configuration
class CatalogModuleConfiguration {
}
