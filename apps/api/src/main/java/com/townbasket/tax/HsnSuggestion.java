package com.townbasket.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * A curated HSN entry with its candidate GST rates. Because one HSN heading can
 * carry different rates depending on qualifiers (loose vs pre-packaged, bread vs
 * biscuits), an entry lists every candidate with its qualifier text — staff pick
 * the one that matches the SKU. A SUGGESTION AID only: the rate saved on the
 * product (validated against the legal slabs) remains the source of truth.
 */
public record HsnSuggestion(String hsn, String description, List<RateOption> options) {

    /** One candidate rate and the qualifier under which it applies. */
    public record RateOption(BigDecimal ratePercent, String qualifier) {
    }
}
