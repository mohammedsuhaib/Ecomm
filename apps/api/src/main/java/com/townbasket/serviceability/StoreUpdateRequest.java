package com.townbasket.serviceability;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Admin edit of the store's operating settings. The form always submits the
 * whole card, so a partial update has nothing to mean. {@code active} and the
 * id are deliberately not editable here.
 *
 * @param supportPhone the store's public contact number — the one optional
 *     field, since a store that hasn't published a number yet must still be
 *     able to save the rest of the card. Blank clears it, and the storefront
 *     then stops offering customers a way to call.
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
        String supportPhone) {
}
