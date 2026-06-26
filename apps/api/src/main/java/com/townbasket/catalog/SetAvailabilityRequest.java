package com.townbasket.catalog;

/**
 * Admin request to toggle availability of a product or variant. Plain record.
 *
 * @param available the new availability flag
 */
public record SetAvailabilityRequest(boolean available) {
}
