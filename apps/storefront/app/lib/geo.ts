// Small geometry helpers for the tracking page.

const EARTH_RADIUS_M = 6_371_000;

/**
 * Great-circle distance between two points, in metres (haversine).
 *
 * <p>Accurate to well under a metre over the distances this app deals in — a
 * 5 km delivery radius — and needs no map SDK, so the "your rider is 800 m
 * away" line works in a build with no Google Maps key at all.
 */
export function distanceMeters(
  a: { lat: number; lng: number },
  b: { lat: number; lng: number },
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}
