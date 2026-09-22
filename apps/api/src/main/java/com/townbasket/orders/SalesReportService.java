package com.townbasket.orders;

import java.time.LocalDate;

/**
 * Admin GST sales-report export: one row per order-item line for every order
 * placed within an inclusive local-date range (matched against
 * {@code placedAt}) whose status is not CANCELLED — invoiced or not. The
 * report is a filing aid over everything sold in the window, so it is
 * deliberately not narrowed to orders that already carry an invoice number.
 *
 * <p>Both renderings share the same rows and the same header block (store
 * name/address/GSTIN from {@code townbasket.invoice.*}, the report title, and
 * the date range); they differ only in output format.
 */
public interface SalesReportService {

    /**
     * Render the report as an .xlsx workbook (header row bold, data rows
     * plain, a bold TOTALS row last).
     *
     * @throws com.townbasket.shared.BusinessRuleException if {@code to} is
     *     before {@code from}
     */
    byte[] renderSalesReportXlsx(LocalDate from, LocalDate to);

    /**
     * Render the report as a landscape PDF with the same rows and totals.
     *
     * @throws com.townbasket.shared.BusinessRuleException if {@code to} is
     *     before {@code from}
     */
    byte[] renderSalesReportPdf(LocalDate from, LocalDate to);
}
