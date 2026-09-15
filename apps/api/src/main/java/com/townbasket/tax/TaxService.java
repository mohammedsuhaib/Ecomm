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
     * Curated HSN → candidate-rate suggestions for the grocery universe
     * (admin product-form prefill). Matches by prefix in both directions
     * ("0403" matches a full "04031000" code; "04" lists the dairy chapter).
     * Returns at most a handful of entries; empty for blank/unknown codes.
     * A suggestion aid only — never a compliance authority.
     */
    List<HsnSuggestion> hsnSuggestions(String hsnCode);

    /**
     * Extract GST from a tax-inclusive gross amount.
     *
     * <p>Two guarantees, both of which hold line by line and therefore on any
     * invoice built by summing lines:
     * <ul>
     *   <li>{@code cgst == sgst} exactly — each is the half-rate levy on the
     *       same value, which is how an intra-state supply is charged (18% is
     *       9% central tax plus 9% State tax), so an invoice can never show one
     *       half larger than the other;</li>
     *   <li>{@code taxableValue + cgst + sgst == grossAmount} exactly, so a
     *       breakdown always re-adds to the price the customer actually paid —
     *       which matters here because prices are MRP-inclusive.</li>
     * </ul>
     *
     * <p>Rounding lands on the taxable value, which may sit a paisa off the
     * textbook {@code grossAmount × 100 / (100 + rate)}.
     *
     * @param grossAmount tax-inclusive amount, ≥ 0
     * @param ratePercent GST rate — must be a valid slab
     */
    TaxBreakdown fromInclusiveAmount(BigDecimal grossAmount, BigDecimal ratePercent);
}
