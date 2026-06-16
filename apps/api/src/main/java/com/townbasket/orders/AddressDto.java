package com.townbasket.orders;

/** Delivery address: a free-text line plus the pin-drop coordinates. */
public record AddressDto(String line, double lat, double lng) {
}
