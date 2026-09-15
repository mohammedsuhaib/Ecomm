package com.townbasket.tax;

import java.math.BigDecimal;

/**
 * Splits the GST on the lines of ONE invoice, keeping that invoice's CGST and
 * SGST totals within a paisa of each other.
 *
 * <p><strong>Why a splitter and not a plain function.</strong> Half of an odd
 * number of paise cannot be split evenly: a ₹600.00 line at 18% carries ₹91.53
 * of tax, so one side gets ₹45.77 and the other ₹45.76. Deciding that per line
 * in isolation — as {@link TaxService#fromInclusiveAmount} must, knowing
 * nothing of its neighbours — means the same side wins every time, and the
 * bias accumulates down the invoice: two such lines put the summary 2 paise
 * out, ten lines put it 10 paise out. The totals still re-add to what the
 * customer paid, but CGST and SGST are no longer halves of the tax, which a
 * GST invoice has to show them as.
 *
 * <p>So each odd paisa goes to whichever side is behind <em>so far on this
 * invoice</em>, which cancels the bias: every line still re-adds to its own
 * gross exactly, and the invoice's two totals end up equal or a single paisa
 * apart however many lines it has.
 *
 * <p><strong>Stateful, and therefore single-use.</strong> One splitter per
 * invoice, used for all of its lines in order; it is not thread-safe and must
 * not be shared between orders. Obtain one from
 * {@link TaxService#invoiceSplitter()}.
 */
public interface InvoiceTaxSplitter {

    /**
     * Extract GST from one tax-inclusive line, biasing the odd paisa towards
     * the side currently behind on this invoice.
     *
     * <p>Same guarantees per line as {@link TaxService#fromInclusiveAmount}:
     * {@code taxableValue + cgst + sgst == grossAmount} exactly.
     *
     * @param grossAmount tax-inclusive line total, ≥ 0
     * @param ratePercent GST rate — must be a valid slab
     */
    TaxBreakdown split(BigDecimal grossAmount, BigDecimal ratePercent);
}
