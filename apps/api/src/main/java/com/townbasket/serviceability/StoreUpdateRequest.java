package com.townbasket.serviceability;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Admin edit of the store's operating settings. The form always submits the
 * whole card, so a partial update has nothing to mean. {@code active} and the
 * id are deliberately not editable here.
 *
 * @param supportPhone the store's public contact number — optional, since a
 *     store that hasn't published a number yet must still be able to save the
 *     rest of the card. Blank clears it, and the storefront then stops offering
 *     customers a way to call.
 * @param gstin the GST registration number, also optional for the same reason:
 *     a store registers once, and until it has, invoices simply omit the line.
 *     Blank clears it. Normalised and validated by {@code Gstin} — spaces and
 *     hyphens off a certificate are accepted and stripped. Editing this never
 *     touches an invoice already issued; each one keeps the GSTIN it was issued
 *     under (see {@code orders.orders.invoice_gstin}).
 */
public record StoreUpdateRequest(
        String name,
        String address,
        double lat,
        double lng,
        int deliveryRadiusMeters,
        LocalTime openingTime,
        LocalTime closingTime,
        BigDecimal minOrderValue,
        String supportPhone,
        String gstin) {
}
