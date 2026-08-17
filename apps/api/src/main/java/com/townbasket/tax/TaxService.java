package com.townbasket.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published API of the tax module: GST slab validation and inclusive-price
 * back-calculation.
 *
 * <p>All amounts are rupees with 2-decimal scale. Rates are percentages
 * (e.g. {@code 5} for 5% GST).
 */
public interface TaxService {

    /**
     * The GST slabs a product may be assigned, per the rate structure in force
     * since the September 2025 rationalisation (0%, 5%, 18%, 40%). Compared by
     * value, so {@code 5} and {@code 5.00} are the same slab.
     */
    List<BigDecimal> gstSlabs();

    /** {@code true} when {@code ratePercent} is exactly one of {@link #gstSlabs()}. */
    boolean isValidGstRate(BigDecimal ratePercent);

    /**
     * Reject a rate outside {@link #gstSlabs()} with a
     * {@link com.townbasket.shared.BusinessRuleException} naming the allowed
     * slabs. {@code null} is rejected too — callers default absent rates to 0
     * <em>before</em> validating.
     */
    void requireValidGstRate(BigDecimal ratePercent);

    /**
     * Extract GST from a tax-inclusive gross amount.
     *
     * <p>Guarantees {@code taxableValue + cgst + sgst == grossAmount} exactly
     * (rounding differences are absorbed by SGST), so line breakdowns always
     * re-add to the price the customer actually paid.
     *
     * @param grossAmount tax-inclusive amount, ≥ 0
     * @param ratePercent GST rate — must be a valid slab
     */
    TaxBreakdown fromInclusiveAmount(BigDecimal grossAmount, BigDecimal ratePercent);
}
