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
     * @param order the order to bill (as returned by the customer-facing API)
     * @return the PDF document bytes
     */
    byte[] renderInvoicePdf(OrderDto order);
}
