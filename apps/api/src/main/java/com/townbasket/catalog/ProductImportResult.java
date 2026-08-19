package com.townbasket.catalog;

import java.util.List;

/**
 * Outcome of a CSV product import: how many products were created, how many
 * were skipped (a product with the same name already exists — imports are
 * re-runnable), and per-row errors for everything else. {@code row} is the
 * 1-based line number in the uploaded file (header = line 1).
 */
public record ProductImportResult(int created, int skipped, List<RowError> errors) {

    /** One failed row/product and the reason it was rejected. */
    public record RowError(int row, String message) {
    }
}
