package com.townbasket.tax.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.tax.HsnSuggestion;
import com.townbasket.tax.InvoiceTaxSplitter;
import com.townbasket.tax.TaxBreakdown;
import com.townbasket.tax.TaxService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the GST back-calculation: extraction from inclusive
 * prices, the taxable + CGST + SGST == gross invariant (including odd-paisa
 * cases), and slab validation. Pure unit test — the calculator is stateless,
 * so no Spring context or database is needed.
 */
class GstCalculatorTest {

    private final TaxService taxService = new GstCalculator(new ObjectMapper());

    @Test
    void extractsFivePercentFromInclusivePrice() {
        // ₹105 at 5% inclusive → ₹100 taxable + ₹2.50 CGST + ₹2.50 SGST.
        TaxBreakdown b = taxService.fromInclusiveAmount(new BigDecimal("105.00"), new BigDecimal("5"));
        assertThat(b.taxableValue()).isEqualByComparingTo("100.00");
        assertThat(b.cgst()).isEqualByComparingTo("2.50");
        assertThat(b.sgst()).isEqualByComparingTo("2.50");
        assertThat(b.totalTax()).isEqualByComparingTo("5.00");
    }

    @Test
    void zeroRateYieldsNoTax() {
        TaxBreakdown b = taxService.fromInclusiveAmount(new BigDecimal("87.00"), BigDecimal.ZERO);
        assertThat(b.taxableValue()).isEqualByComparingTo("87.00");
        assertThat(b.totalTax()).isEqualByComparingTo("0.00");
    }

    @Test
    void anInvoiceKeepsCgstAndSgstBalancedAcrossItsLines() {
        // The exact order QA failed TC-TAX-005 on: a ₹600.00 line at 18% and a
        // ₹300.99 line at 5%. Both carry an odd number of paise of tax, and
        // per-line extraction gave the odd paisa to CGST twice over, so the
        // invoice summary showed CGST ₹52.94 against SGST ₹52.92 — 2 paise
        // apart, breaking the one-paisa rule partsAlwaysReAddToGross asserts
        // per line.
        InvoiceTaxSplitter splitter = taxService.invoiceSplitter();
        TaxBreakdown eighteen = splitter.split(new BigDecimal("600.00"), new BigDecimal("18"));
        TaxBreakdown five = splitter.split(new BigDecimal("300.99"), new BigDecimal("5"));

        // Each line still re-adds to its own gross, exactly.
        assertThat(eighteen.taxableValue()).isEqualByComparingTo("508.47");
        assertThat(eighteen.totalTax()).isEqualByComparingTo("91.53");
        assertThat(five.taxableValue()).isEqualByComparingTo("286.66");
        assertThat(five.totalTax()).isEqualByComparingTo("14.33");

        // ...and the invoice's two halves now come out equal.
        BigDecimal cgst = eighteen.cgst().add(five.cgst());
        BigDecimal sgst = eighteen.sgst().add(five.sgst());
        assertThat(cgst).as("invoice CGST").isEqualByComparingTo("52.93");
        assertThat(sgst).as("invoice SGST").isEqualByComparingTo("52.93");
        assertThat(cgst.add(sgst)).as("total GST is unchanged").isEqualByComparingTo("105.86");
    }

    @Test
    void invoiceTotalsStayWithinOnePaisaHoweverManyOddLinesThereAre() {
        // The old bias grew by a paisa per odd-paise line, so the failure got
        // worse the bigger the basket. Twenty such lines is the case that would
        // have been 20 paise out.
        InvoiceTaxSplitter splitter = taxService.invoiceSplitter();
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (int i = 0; i < 20; i++) {
            TaxBreakdown b = splitter.split(new BigDecimal("600.00"), new BigDecimal("18"));
            cgst = cgst.add(b.cgst());
            sgst = sgst.add(b.sgst());
            tax = tax.add(b.totalTax());
        }
        assertThat(cgst.subtract(sgst).abs())
                .as("CGST/SGST skew over 20 odd-paise lines")
                .isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(cgst.add(sgst)).as("no tax invented or lost").isEqualByComparingTo(tax);
    }

    @Test
    void aSplitterIsPerInvoiceAndStartsFresh() {
        // Two orders must not inherit each other's bias, and a single-line
        // invoice must match the standalone calculation exactly.
        TaxBreakdown standalone =
                taxService.fromInclusiveAmount(new BigDecimal("600.00"), new BigDecimal("18"));
        for (int i = 0; i < 2; i++) {
            TaxBreakdown first = taxService.invoiceSplitter()
                    .split(new BigDecimal("600.00"), new BigDecimal("18"));
            assertThat(first.cgst()).isEqualByComparingTo(standalone.cgst());
            assertThat(first.sgst()).isEqualByComparingTo(standalone.sgst());
        }
    }

    @Test
    void partsAlwaysReAddToGross() {
        // Sweep awkward gross amounts across every slab: the breakdown must
        // re-add to the gross exactly, and SGST may differ from CGST by at
        // most one paisa (it absorbs the rounding remainder).
        for (BigDecimal rate : taxService.gstSlabs()) {
            for (String gross : new String[] {"0.01", "0.99", "1.00", "33.33", "99.99", "101.01", "2499.55"}) {
                TaxBreakdown b = taxService.fromInclusiveAmount(new BigDecimal(gross), rate);
                assertThat(b.taxableValue().add(b.cgst()).add(b.sgst()))
                        .as("gross %s at %s%%", gross, rate)
                        .isEqualByComparingTo(gross);
                assertThat(b.cgst().subtract(b.sgst()).abs())
                        .as("CGST/SGST skew for %s at %s%%", gross, rate)
                        .isLessThanOrEqualTo(new BigDecimal("0.01"));
            }
        }
    }

    @Test
    void rejectsRatesOutsideTheSlabs() {
        assertThat(taxService.isValidGstRate(new BigDecimal("5.00"))).isTrue(); // value-compare, not equals()
        assertThat(taxService.isValidGstRate(new BigDecimal("12"))).isFalse(); // pre-2025 slab, no longer legal
        assertThatThrownBy(() -> taxService.requireValidGstRate(new BigDecimal("7")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("GST rate");
        assertThatThrownBy(() -> taxService.fromInclusiveAmount(new BigDecimal("10"), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> taxService.fromInclusiveAmount(new BigDecimal("-1"), new BigDecimal("5")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void hsnSuggestionsMatchByPrefixBothWays() {
        // A full 8-digit code matches its curated 4-digit heading.
        List<HsnSuggestion> byFullCode = taxService.hsnSuggestions("04031000");
        assertThat(byFullCode).anySatisfy(s -> assertThat(s.hsn()).isEqualTo("0403"));

        // A bare 2-digit chapter lists its headings (capped).
        assertThat(taxService.hsnSuggestions("04"))
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.hsn()).startsWith("04"))
                .hasSizeLessThanOrEqualTo(8);

        // Non-digits are stripped; every rate offered is a legal slab.
        assertThat(taxService.hsnSuggestions(" 1101 "))
                .flatExtracting(HsnSuggestion::options)
                .allSatisfy(o -> assertThat(
                        taxService.isValidGstRate(((HsnSuggestion.RateOption) o).ratePercent())).isTrue());

        // Blank and unknown codes yield nothing.
        assertThat(taxService.hsnSuggestions(null)).isEmpty();
        assertThat(taxService.hsnSuggestions("9999")).isEmpty();
    }
}
