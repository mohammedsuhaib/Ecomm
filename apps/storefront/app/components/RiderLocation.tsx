'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { GoogleMap, Marker, useJsApiLoader } from '@react-google-maps/api';
import { distanceMeters } from '@/app/lib/geo';
import { formatDistance } from '@/app/lib/format';
import type { Order, RiderLocation as RiderFix } from '@/app/lib/types';

// Read once at module eval — a plain string check, no SDK import, so it is
// SSR/build safe (same pattern as LocationPicker and firebase.ts).
const MAPS_API_KEY = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY ?? '';

/** Closer than this and "about 80 m away" reads worse than "almost there". */
const NEARBY_M = 100;

/**
 * "Your rider is on the way" — where the rider is, relative to the customer,
 * while the order is out for delivery.
 *
 * <p>The position rides the order itself: {@code order.riderLocation} is set by
 * the API only while OUT_FOR_DELIVERY, only for the assigned rider, and only
 * while their last fix is fresh, and the order page already re-fetches the
 * order every few seconds for status. So this component has no transport of
 * its own — it renders whatever the latest order carries, and disappears the
 * moment the API stops including a position (delivered, or the rider's phone
 * went quiet).
 *
 * <p>Two layers, so it is useful in every build: the distance and freshness
 * line needs no map SDK at all (haversine in lib/geo.ts), and the map on top
 * appears only when {@code NEXT_PUBLIC_GOOGLE_MAPS_API_KEY} is present — CI and
 * the QA stack build without one and still get the text.
 */
export default function RiderLocation({ order }: { order: Order }) {
  const t = useTranslations('order');
  // Never init the map during SSR/prerender: gate the map subtree on mount.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  // Ticks once a second so "updated 12 s ago" keeps counting between polls —
  // that movement is what tells the customer the number is live, not stuck.
  const [nowMs, setNowMs] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const fix = order.riderLocation;
  if (order.status !== 'OUT_FOR_DELIVERY' || !fix) return null;

  const home = { lat: order.address.lat, lng: order.address.lng };
  const metres = distanceMeters(fix, home);
  const ageSeconds = Math.max(0, Math.round((nowMs - Date.parse(fix.recordedAt)) / 1000));

  return (
    <section className="rider-card" aria-label={t('riderTitle')}>
      <div className="rider-card-head">
        <h2 className="rider-card-title">
          <span aria-hidden>🛵</span> {t('riderTitle')}
        </h2>
        <span className="muted rider-card-age">
          {ageSeconds < 60
            ? t('riderUpdatedSeconds', { seconds: ageSeconds })
            : t('riderUpdatedMinutes', { minutes: Math.floor(ageSeconds / 60) })}
        </span>
      </div>
      <p className="rider-card-distance">
        {metres < NEARBY_M
          ? t('riderNearby')
          : t('riderDistance', { distance: formatDistance(metres) })}
      </p>
      {mounted && MAPS_API_KEY && <RiderMap rider={fix} home={home} />}
    </section>
  );
}

// Separate component so the Google Maps SDK hooks only ever run when the key
// is present AND we are mounted in the browser (the parent gates this).
function RiderMap({
  rider,
  home,
}: {
  rider: RiderFix;
  home: { lat: number; lng: number };
}) {
  const t = useTranslations('order');
  // Same loader id as LocationPicker, so the two never fight over which
  // <script> owns the SDK when both have been on the page in one session.
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'tb-google-maps',
    googleMapsApiKey: MAPS_API_KEY,
  });
  const mapRef = useRef<google.maps.Map | null>(null);

  // Keep both pins in view as the rider moves. Padding so a pin is never
  // clipped under the map's edge; a minimum zoom so two pins a street apart
  // don't produce a map of the whole city.
  const fit = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;
    const bounds = new google.maps.LatLngBounds();
    bounds.extend(rider);
    bounds.extend(home);
    map.fitBounds(bounds, 48);
  }, [rider, home]);

  useEffect(() => {
    if (isLoaded) fit();
  }, [isLoaded, fit]);

  if (loadError) {
    // Bad key, blocked domain, no network: the distance line above already
    // says what matters, so degrade to nothing rather than to an error box.
    return null;
  }
  if (!isLoaded) {
    return (
      <div className="rider-map rider-map-loading">
        <span className="muted">{t('riderMapLoading')}</span>
      </div>
    );
  }

  return (
    <GoogleMap
      mapContainerClassName="rider-map"
      center={rider}
      zoom={15}
      onLoad={(map) => {
        mapRef.current = map;
        fit();
      }}
      onUnmount={() => {
        mapRef.current = null;
      }}
      options={{
        streetViewControl: false,
        mapTypeControl: false,
        fullscreenControl: false,
        clickableIcons: false,
        // A finger dragging down the order page must scroll the page, not pan
        // the map — the map is a picture here, not a control.
        gestureHandling: 'cooperative',
      }}
    >
      <Marker position={rider} title={t('riderMarker')} label={{ text: '🛵', fontSize: '18px' }} />
      <Marker position={home} title={t('homeMarker')} label={{ text: '🏠', fontSize: '18px' }} />
    </GoogleMap>
  );
}
