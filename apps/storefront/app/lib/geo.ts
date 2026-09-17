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

/**
 * Roads are not straight lines. Over a town's street grid the distance
 * actually ridden runs about 1.3× the crow-flies figure — the "circuity"
 * factor transport planners use for urban trips, and a fair fit for a 5 km
 * neighbourhood delivery.
 */
const ROAD_FACTOR = 1.3;

/**
 * Average two-wheeler speed across a delivery leg in town traffic, including
 * the stops: junctions, the last hundred metres of finding the gate. Riders
 * cruise faster than this; the leg as a whole does not.
 */
const AVG_SPEED_KMH = 18;

/**
 * Roughly how many minutes the rider is away, from the straight-line distance.
 *
 * <p>An estimate, and presented as one ("About N minutes"). A routed answer
 * from a directions API would be tighter, but it would put a billable
 * server-side call behind every ~6 s poll of every open tracking page, and
 * would still need this fallback wherever there is no key — CI and the QA
 * stack build without one. For "is it worth putting the kettle on", crow-flies
 * × road factor ÷ town speed is honest enough, and it is the same answer in
 * every build.
 *
 * <p>Rounded UP and never below one: an ETA that under-promises by a minute is
 * a pleasant surprise, one that over-promises is a customer at the window.
 */
export function estimateMinutesAway(meters: number): number {
  const metresPerMinute = (AVG_SPEED_KMH * 1000) / 60;
  return Math.max(1, Math.ceil((meters * ROAD_FACTOR) / metresPerMinute));
}
