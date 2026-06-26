package com.townbasket.catalog.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Module-internal wiring for product-name transliteration. Active only when
 * {@code townbasket.catalog.transliteration.enabled=true} — so tests and any
 * deployment that opts out never create the HTTP adapter or schedule the backfill
 * (no external calls). Enables scheduling for {@link ProductNameBackfillJob}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "townbasket.catalog.transliteration", name = "enabled", havingValue = "true")
@EnableScheduling
@EnableConfigurationProperties(TransliterationProperties.class)
class CatalogTransliterationConfiguration {
}
