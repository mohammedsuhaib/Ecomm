package com.townbasket.tax;

import java.math.BigDecimal;

/**
 * GST extracted from a tax-inclusive amount. Invariant (enforced by the
 * calculator): {@code taxableValue + cgst + sgst} equals the gross amount the
 * breakdown was computed from.
 *
 * <p>Intra-state supply only (single-town delivery), so the split is always
 * CGST + SGST — there is no IGST leg.
 */
public record TaxBreakdown(
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst) {

    public BigDecimal totalTax() {
        return cgst.add(sgst);
    }
}
