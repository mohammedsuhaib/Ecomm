package com.townbasket.catalog.internal;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fills in missing Kannada product names ({@code catalog.products.name_kn}) on a
 * periodic sweep. This is the population mechanism for transliterations: it
 * drains the go-live catalogue in batches and picks up any products added later
 * (e.g. via bulk import) without a write path of its own. Idempotent — once a
 * product has a Kannada name it is never re-fetched; a sweep that finds nothing
 * is a no-op.
 *
 * <p>Active only when {@code townbasket.catalog.transliteration.enabled=true}
 * (see {@link CatalogTransliterationConfiguration}), so tests and opted-out
 * deployments make no external calls. Transliteration runs outside any DB
 * transaction; each result is persisted via {@link ProductNameWriter}.
 *
 * <p>For a faster initial drain of a large catalogue, lower
 * {@code backfill-interval-ms} and/or raise {@code batch-size} temporarily.
 */
@Component
@ConditionalOnProperty(prefix = "townbasket.catalog.transliteration", name = "enabled", havingValue = "true")
class ProductNameBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(ProductNameBackfillJob.class);

    private final ProductRepository products;
    private final ProductNameTransliterator transliterator;
    private final ProductNameWriter writer;
    private final TransliterationProperties props;

    ProductNameBackfillJob(ProductRepository products,
                           ProductNameTransliterator transliterator,
                           ProductNameWriter writer,
                           TransliterationProperties props) {
        this.products = products;
        this.transliterator = transliterator;
        this.writer = writer;
        this.props = props;
    }

    @Scheduled(
            initialDelayString = "${townbasket.catalog.transliteration.backfill-initial-delay-ms:30000}",
            fixedDelayString = "${townbasket.catalog.transliteration.backfill-interval-ms:300000}")
    void backfillMissingNames() {
        List<ProductEntity> batch = products.findByNameKnIsNull(PageRequest.of(0, props.batchSize()));
        if (batch.isEmpty()) {
            return;
        }
        int filled = 0;
        for (ProductEntity product : batch) {
            // External call OUTSIDE any transaction; persist per product.
            var kannada = transliterator.toKannada(product.getName());
            if (kannada.isPresent()) {
                writer.save(product.getId(), kannada.get());
                filled++;
            }
        }
        log.info("Transliteration backfill: filled {} of {} pending product name(s)", filled, batch.size());
    }
}
