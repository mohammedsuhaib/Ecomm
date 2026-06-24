package com.townbasket.catalog.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the product-name transliteration backfill. Bound from
 * {@code townbasket.catalog.transliteration.*}. Only active when the feature is
 * enabled (see {@link CatalogTransliterationConfiguration}); the scheduling
 * timings are read directly by the job's {@code @Scheduled} placeholders.
 *
 * @param endpoint  transliteration HTTP endpoint (default: Google Input Tools)
 * @param itc       input-tool code selecting the target script (Kannada)
 * @param batchSize products transliterated per backfill sweep
 */
@ConfigurationProperties(prefix = "townbasket.catalog.transliteration")
record TransliterationProperties(String endpoint, String itc, int batchSize) {

    TransliterationProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://inputtools.google.com/request";
        }
        if (itc == null || itc.isBlank()) {
            itc = "kn-t-i0-und";
        }
        if (batchSize <= 0) {
            batchSize = 25;
        }
    }
}
