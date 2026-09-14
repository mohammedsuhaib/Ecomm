package com.townbasket.orders.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.townbasket.shared.BusinessRuleException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the two pure pieces of order numbering: the customer-facing
 * order code and the GST invoice number's financial-year labelling. No Spring,
 * no database — the parts that decide what a customer reads and what an auditor
 * checks should be verifiable on their own.
 */
class OrderNumberingTest {

    // ---- order code --------------------------------------------------------

    @Test
    void codeIsEightUnambiguousSymbols() {
        String code = OrderCodes.newCode();

        assertThat(code).hasSize(OrderCodes.CODE_LENGTH);
        // Crockford base32: no I, L, O or U, so nothing can be misheard on the
        // phone as something else (or misread off a printed slip).
        assertThat(code).containsPattern("^[0-9A-HJKMNP-TV-Z]{8}$");
        assertThat(code).doesNotContainAnyWhitespaces();
    }

    @Test
    void codesDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            seen.add(OrderCodes.newCode());
        }
        // 40 bits: a collision in 5k draws would mean the generator is broken,
        // not unlucky. The unique index is still the real arbiter.
        assertThat(seen).hasSize(5_000);
    }

    @Test
    void normalizeFoldsTheMistakesPeopleActuallyMake() {
        // Lower case, and the hyphen a customer adds when reading it out.
        assertThat(OrderCodes.normalize("7k4m-2qx9")).isEqualTo("7K4M2QX9");
        assertThat(OrderCodes.normalize("  7K4M 2QX9  ")).isEqualTo("7K4M2QX9");
        // The Crockford substitutions: I and L heard as one, O as zero.
        assertThat(OrderCodes.normalize("OIL")).isEqualTo("011");
        // Nothing usable in, nothing out — the caller turns this into a pattern
        // that matches no code rather than every code.
        assertThat(OrderCodes.normalize("???")).isEmpty();
        assertThat(OrderCodes.normalize(null)).isEmpty();
    }

    // ---- invoice number ----------------------------------------------------

    @Test
    void financialYearRunsAprilToMarch() {
        // First and last day of FY 25-26.
        assertThat(InvoiceNumbers.financialYear(LocalDate.of(2025, 4, 1))).isEqualTo("25-26");
        assertThat(InvoiceNumbers.financialYear(LocalDate.of(2026, 3, 31))).isEqualTo("25-26");
        // The boundary: the next day starts a new series.
        assertThat(InvoiceNumbers.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("26-27");
        // A January date belongs to the year that STARTED the previous April —
        // the case a naive "use the calendar year" would get wrong.
        assertThat(InvoiceNumbers.financialYear(LocalDate.of(2024, 1, 1))).isEqualTo("23-24");
        // Century rollover still reads sensibly.
        assertThat(InvoiceNumbers.financialYear(LocalDate.of(2099, 12, 31))).isEqualTo("99-00");
    }

    @Test
    void invoiceNumberIsPaddedAndWithinTheGstLimit() {
        assertThat(InvoiceNumbers.format("TB", "25-26", 1)).isEqualTo("TB/25-26/00001");
        assertThat(InvoiceNumbers.format("TB", "25-26", 42)).isEqualTo("TB/25-26/00042");
        // Past the padding width it grows rather than truncating — a wrong
        // number would be worse than a long one.
        assertThat(InvoiceNumbers.format("TB", "25-26", 123456)).isEqualTo("TB/25-26/123456");
        assertThat(InvoiceNumbers.format("TB", "25-26", 1).length())
                .isLessThanOrEqualTo(InvoiceNumbers.MAX_LENGTH);
    }

    @Test
    void anOverlongPrefixFailsFastInsteadOfBillingNonCompliantly() {
        // Rule 46(b) caps the number at 16 characters. A misconfigured prefix
        // must break at issue time, not quietly produce invalid tax invoices.
        assertThatThrownBy(() -> InvoiceNumbers.format("TOWNBASKET", "25-26", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("16");
    }
}
