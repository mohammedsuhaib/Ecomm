package com.townbasket.catalog;

import com.townbasket.tax.HsnSuggestion;
import java.math.BigDecimal;
import java.util.List;

/**
 * Admin product-form prefill for an HSN code: the rate this catalog already
 * uses for it (the strongest signal — your own prior decision), plus the
 * curated candidate rates from the tax module for HSNs not seen before.
 */
public record HsnRateSuggestionsDto(BigDecimal catalogRate, List<HsnSuggestion> suggestions) {
}
