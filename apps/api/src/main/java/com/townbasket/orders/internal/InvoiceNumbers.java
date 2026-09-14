package com.townbasket.orders.internal;

import com.townbasket.shared.BusinessRuleException;
import java.time.LocalDate;
import java.time.Month;

/**
 * Financial-year labelling and formatting for GST invoice numbers — the pure
 * part of the numbering, kept separate from the counter so it can be reasoned
 * about (and tested) without a database.
 *
 * <p>Rule 46(b) of the CGST Rules asks for a consecutive serial number of at
 * most sixteen characters, unique for a financial year. India's financial year
 * runs 1 April to 31 March, so a date's year alone does not identify it:
 * 31 March 2026 belongs to FY 25-26 and 1 April 2026 starts FY 26-27.
 */
final class InvoiceNumbers {

    /** CGST Rule 46(b): a tax invoice number may not exceed sixteen characters. */
    static final int MAX_LENGTH = 16;

    /** Zero-padding width for the per-year sequence; it grows past this on its own. */
    private static final int SEQ_WIDTH = 5;

    private InvoiceNumbers() {
    }

    /**
     * The Indian financial-year label for {@code date}, e.g. {@code "25-26"}
     * for any date from 1 April 2025 through 31 March 2026.
     *
     * @param date the date the invoice is issued, in the store's zone
     */
    static String financialYear(LocalDate date) {
        int startYear = date.getMonth().getValue() >= Month.APRIL.getValue()
                ? date.getYear()
                : date.getYear() - 1;
        return String.format("%02d-%02d", startYear % 100, (startYear + 1) % 100);
    }

    /**
     * Assemble the invoice number, e.g. {@code "TB/25-26/00001"}.
     *
     * @throws BusinessRuleException if the configured prefix pushes the result
     *     past the sixteen-character limit — a misconfiguration that would
     *     otherwise only surface as a non-compliant invoice
     */
    static String format(String prefix, String financialYear, long sequence) {
        String number = prefix + "/" + financialYear + "/"
                + String.format("%0" + SEQ_WIDTH + "d", sequence);
        if (number.length() > MAX_LENGTH) {
            throw new BusinessRuleException(
                    "Invoice number \"" + number + "\" is " + number.length()
                            + " characters; GST allows at most " + MAX_LENGTH
                            + ". Shorten townbasket.invoice.series-prefix.");
        }
        return number;
    }
}
