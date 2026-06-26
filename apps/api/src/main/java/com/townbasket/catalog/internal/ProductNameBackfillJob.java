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

    /** Upper bound on how many sweeps to skip while the endpoint is unavailable. */
    private static final int MAX_SWEEPS_TO_SKIP = 15;

    private final ProductRepository products;
    private final ProductNameTransliterator transliterator;
    private final ProductNameWriter writer;
    private final TransliterationProperties props;

    // Simple back-off: if a whole sweep yields nothing (endpoint down/blocked),
    // skip an increasing number of subsequent sweeps instead of hammering it.
    private int consecutiveFailedSweeps = 0;
    private int sweepsToSkip = 0;

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
        if (sweepsToSkip > 0) {
            sweepsToSkip--;
            return;
        }
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
        if (filled == 0) {
            // Whole sweep produced nothing — the endpoint is almost certainly
            // unreachable/blocked (e.g. TLS not trusted). Back off so we neither
            // hammer it nor flood the log; resumes promptly once names come back.
            consecutiveFailedSweeps++;
            sweepsToSkip = Math.min(consecutiveFailedSweeps, MAX_SWEEPS_TO_SKIP);
            log.warn("Transliteration backfill: 0 of {} pending name(s) filled; "
                    + "transliteration endpoint appears unavailable — backing off {} sweep(s)",
                    batch.size(), sweepsToSkip);
        } else {
            consecutiveFailedSweeps = 0;
            log.info("Transliteration backfill: filled {} of {} pending product name(s)", filled, batch.size());
        }
    }
}
