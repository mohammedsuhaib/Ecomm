package com.townbasket.tax.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.tax.HsnSuggestion;
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
    void mixedRateInvoiceShowsEqualCgstAndSgst() {
        // The exact order QA failed TC-TAX-005 on: a ₹600.00 line at 18% and a
        // ₹300.99 line at 5%. Halving each line's tax gave the odd paisa to
        // CGST twice over, so the summary showed CGST ₹52.94 against SGST
        // ₹52.92. Computing each half directly from the gross makes them equal
        // per line, so the invoice is balanced by construction.
        TaxBreakdown eighteen = taxService.fromInclusiveAmount(
                new BigDecimal("600.00"), new BigDecimal("18"));
        TaxBreakdown five = taxService.fromInclusiveAmount(
                new BigDecimal("300.99"), new BigDecimal("5"));

        assertThat(eighteen.cgst()).isEqualByComparingTo("45.76");
        assertThat(eighteen.sgst()).isEqualByComparingTo("45.76");
        assertThat(five.cgst()).isEqualByComparingTo("7.17");
        assertThat(five.sgst()).isEqualByComparingTo("7.17");

        BigDecimal cgst = eighteen.cgst().add(five.cgst());
        BigDecimal sgst = eighteen.sgst().add(five.sgst());
        BigDecimal taxable = eighteen.taxableValue().add(five.taxableValue());

        // The figures QA said the invoice should show.
        assertThat(cgst).as("invoice CGST").isEqualByComparingTo("52.93");
        assertThat(sgst).as("invoice SGST").isEqualByComparingTo("52.93");
        assertThat(cgst.add(sgst)).as("total GST").isEqualByComparingTo("105.86");
        assertThat(taxable).as("total taxable value").isEqualByComparingTo("795.13");
        assertThat(taxable.add(cgst).add(sgst)).as("invoice foots to what was paid")
                .isEqualByComparingTo("900.99");
    }

    @Test
    void theTwoHalvesAreAlwaysEqual() {
        // The invariant that replaced "within one paisa": with both halves
        // derived from the same expression there is no odd paisa to award, so
        // no basket of any size can drift them apart. The taxable value carries
        // the rounding instead, within a paisa of the textbook figure.
        for (BigDecimal rate : taxService.gstSlabs()) {
            for (String gross : new String[] {
                    "0.01", "0.07", "0.99", "1.00", "33.33", "99.99",
                    "101.01", "300.99", "600.00", "2499.55"}) {
                TaxBreakdown b = taxService.fromInclusiveAmount(new BigDecimal(gross), rate);
                assertThat(b.cgst())
                        .as("CGST == SGST for %s at %s%%", gross, rate)
                        .isEqualByComparingTo(b.sgst());
                if (rate.signum() != 0) {
                    BigDecimal textbook = new BigDecimal(gross)
                            .multiply(new BigDecimal("100"))
                            .divide(new BigDecimal("100").add(rate), 2, java.math.RoundingMode.HALF_UP);
                    assertThat(b.taxableValue().subtract(textbook).abs())
                            .as("taxable value for %s at %s%% stays within a paisa of G×100/(100+r)",
                                    gross, rate)
                            .isLessThanOrEqualTo(new BigDecimal("0.01"));
                }
            }
        }
    }

    @Test
    void manyOddLinesNeverDriftTheInvoiceApart() {
        // Twenty of the line that used to bias CGST: the old code was 20 paise
        // out here, and nothing about a longer invoice can move these now.
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        for (int i = 0; i < 20; i++) {
            TaxBreakdown b = taxService.fromInclusiveAmount(
                    new BigDecimal("600.00"), new BigDecimal("18"));
            cgst = cgst.add(b.cgst());
            sgst = sgst.add(b.sgst());
        }
        assertThat(cgst).as("invoice CGST over 20 lines").isEqualByComparingTo(sgst);
    }

    @Test
    void partsAlwaysReAddToGross() {
        // Sweep awkward gross amounts across every slab: the breakdown must
        // re-add to the gross exactly, so an invoice always foots to what the
        // customer paid. (The two halves being EQUAL is asserted over a wider
        // sweep by theTwoHalvesAreAlwaysEqual.)
        for (BigDecimal rate : taxService.gstSlabs()) {
            for (String gross : new String[] {"0.01", "0.99", "1.00", "33.33", "99.99", "101.01", "2499.55"}) {
                TaxBreakdown b = taxService.fromInclusiveAmount(new BigDecimal(gross), rate);
                assertThat(b.taxableValue().add(b.cgst()).add(b.sgst()))
                        .as("gross %s at %s%%", gross, rate)
                        .isEqualByComparingTo(gross);
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
