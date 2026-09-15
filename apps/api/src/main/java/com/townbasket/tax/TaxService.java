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
     * <p>Guarantees {@code taxableValue + cgst + sgst == grossAmount} exactly,
     * so a breakdown always re-adds to the price the customer actually paid. An
     * odd paisa of tax cannot be halved, and this method gives it to CGST.
     *
     * <p><strong>For a single amount only.</strong> Splitting each line of a
     * multi-line invoice with this method biases every one of them the same
     * way, so the invoice's CGST and SGST totals drift a paisa apart per
     * odd-paise line — use {@link #invoiceSplitter()} for anything that will be
     * summed into an invoice.
     *
     * @param grossAmount tax-inclusive amount, ≥ 0
     * @param ratePercent GST rate — must be a valid slab
     */
    TaxBreakdown fromInclusiveAmount(BigDecimal grossAmount, BigDecimal ratePercent);

    /**
     * A fresh {@link InvoiceTaxSplitter} for the lines of one invoice, which
     * keeps that invoice's CGST and SGST totals within a paisa of each other.
     * Stateful and single-use — see {@link InvoiceTaxSplitter}.
     */
    InvoiceTaxSplitter invoiceSplitter();
}
