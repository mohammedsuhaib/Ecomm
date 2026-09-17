package com.townbasket.delivery;

/**
 * Body for {@code PUT /delivery/location} — the rider's current position, as
 * the phone's Geolocation API reported it.
 *
 * @param accuracyMeters the phone's own radius estimate; optional, stored for a
 *                       later UI and not shown to the customer today
 */
public record LocationRequest(double lat, double lng, Double accuracyMeters) {
}
