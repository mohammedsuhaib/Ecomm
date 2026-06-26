package com.townbasket.identity;

/**
 * A saved delivery address belonging to a user. {@code label} is optional;
 * {@code isDefault} marks the user's single default address.
 */
public record SavedAddressDto(
        Long id,
        String label,
        String line,
        double lat,
        double lng,
        boolean isDefault) {
}
