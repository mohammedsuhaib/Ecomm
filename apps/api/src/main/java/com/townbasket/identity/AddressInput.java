package com.townbasket.identity;

/**
 * Create/update payload for a saved address. {@code label} and {@code isDefault}
 * are optional ({@code isDefault} defaults to false). When {@code isDefault} is
 * true, the user's other addresses are un-defaulted (single default rule).
 */
public record AddressInput(
        String label,
        String line,
        double lat,
        double lng,
        Boolean isDefault) {
}
