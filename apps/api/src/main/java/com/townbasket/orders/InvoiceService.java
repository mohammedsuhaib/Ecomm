package com.townbasket.orders;

/**
 * Renders a customer invoice for an order. The orders module owns the invoice
 * because it is a pure presentation of an {@link OrderDto} (no other module's
 * data is needed). The result is a self-contained PDF document.
 */
public interface InvoiceService {

    /**
     * Render the given order as a PDF invoice.
     *
     * @param order the order to bill, which must already carry an issued
     *     invoice number — call {@link OrderService#issueInvoice} first. A
     *     rendered invoice without a number from the per-financial-year series
     *     would not satisfy CGST Rule 46(b), so this is rejected rather than
     *     improvised.
     * @return the PDF document bytes
     * @throws com.townbasket.shared.BusinessRuleException if no invoice has been
     *     issued for the order
     */
    byte[] renderInvoicePdf(OrderDto order);
}
