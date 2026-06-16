/**
 * {@code serviceability} module — stores with lat/lng, a configurable
 * {@code delivery_radius_m} (default 5,000) and operating hours.
 *
 * <p>Distance check (Haversine) enforced at address selection and again at
 * checkout. Geocoding sits behind a {@code GeocodingProvider} port.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Serviceability")
package com.townbasket.serviceability;
