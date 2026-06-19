'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { GoogleMap, Marker, useJsApiLoader } from '@react-google-maps/api';

// F7 — Map pin-drop that replaces the old typed latitude/longitude inputs on
// checkout and the saved-address forms. It is the single source of truth for
// the delivery coordinates used by the 5 km serviceability re-check + order
// address.
//
// CRITICAL (SSR/CI-safe): nothing here touches `window`, `navigator`, or any
// Google global at module load. The Google Maps SDK is ONLY imported/mounted
// in the browser, AND ONLY when `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` is present.
// CI builds with NO key → the fallback (geolocation button + read-only
// readout) renders and the build succeeds without ever loading the map.

export interface LocationPickerProps {
  /** Current latitude as a string (form state), '' when unset. */
  lat: string;
  /** Current longitude as a string (form state), '' when unset. */
  lng: string;
  /** Called with the new numeric coordinates whenever the pin moves. */
  onChange: (lat: number, lng: number) => void;
}

// Read once at module eval. This is a plain string check — it does NOT import
// the SDK, so it is SSR/build safe (mirrors firebase.ts `isFirebaseConfigured`).
const MAPS_API_KEY = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY ?? '';

// Default map centre when no pin is set yet: the store's city (Mysuru). Only
// used for the initial map viewport; the pin itself is only placed on user
// action, so coords stay unset until they actually choose a point.
const DEFAULT_CENTER = { lat: 12.2958, lng: 76.6394 };

function parseCoords(lat: string, lng: string): { lat: number; lng: number } | null {
  const la = Number.parseFloat(lat);
  const ln = Number.parseFloat(lng);
  if (
    Number.isNaN(la) ||
    Number.isNaN(ln) ||
    la < -90 ||
    la > 90 ||
    ln < -180 ||
    ln > 180
  ) {
    return null;
  }
  return { lat: la, lng: ln };
}

export default function LocationPicker({ lat, lng, onChange }: LocationPickerProps) {
  // Mounted flag: guarantees we never init the map during SSR/prerender. The
  // map subtree is gated on this AND on the key being present.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const hasKey = MAPS_API_KEY.length > 0;
  const coords = parseCoords(lat, lng);

  // While the map (or geolocation) is busy locating, show feedback.
  const [locating, setLocating] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);

  const useCurrentLocation = useCallback(
    (onLocated?: (lat: number, lng: number) => void) => {
      setGeoError(null);
      if (typeof navigator === 'undefined' || !navigator.geolocation) {
        setGeoError('Your browser doesn’t support location.');
        return;
      }
      setLocating(true);
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setLocating(false);
          const la = pos.coords.latitude;
          const ln = pos.coords.longitude;
          onChange(la, ln);
          onLocated?.(la, ln);
        },
        () => {
          setLocating(false);
          setGeoError(
            'Location permission was denied. Please allow location access and try again.',
          );
        },
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
      );
    },
    [onChange],
  );

  // ---- Fallback (no key, or before mount): geolocation button + readout ----
  if (!hasKey || !mounted) {
    return (
      <div className="location-picker">
        <div className="location-picker-fallback">
          <button
            type="button"
            className="btn btn-block"
            onClick={() => useCurrentLocation()}
            disabled={locating || !mounted}
          >
            {locating ? 'Locating…' : '📍 Use my current location'}
          </button>
          {geoError && <span className="add-error">{geoError}</span>}
          <p className="location-readout muted">
            {coords
              ? `Pinned: ${coords.lat.toFixed(4)}, ${coords.lng.toFixed(4)}`
              : 'No location set yet.'}
          </p>
        </div>
      </div>
    );
  }

  // ---- Map path (key present + mounted client-side) ----
  return (
    <MapPicker
      coords={coords}
      onChange={onChange}
      useCurrentLocation={useCurrentLocation}
      locating={locating}
      geoError={geoError}
    />
  );
}

// Separate component so the Google Maps SDK hooks are only ever rendered when
// the key is present AND we are mounted in the browser (the parent gates this).
function MapPicker({
  coords,
  onChange,
  useCurrentLocation,
  locating,
  geoError,
}: {
  coords: { lat: number; lng: number } | null;
  onChange: (lat: number, lng: number) => void;
  useCurrentLocation: (onLocated?: (lat: number, lng: number) => void) => void;
  locating: boolean;
  geoError: string | null;
}) {
  // The Google Maps hooks/components below only run because the parent gated
  // this subtree on `mounted && hasKey` — so useJsApiLoader (which injects the
  // <script> tag) never fires during SSR/prerender. The static import above is
  // itself SSR-safe (the package touches no browser global at module load).
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'tb-google-maps',
    googleMapsApiKey: MAPS_API_KEY,
  });

  const mapRef = useRef<google.maps.Map | null>(null);
  const center = coords ?? DEFAULT_CENTER;

  const onMapClick = useCallback(
    (e: google.maps.MapMouseEvent) => {
      if (e.latLng) onChange(e.latLng.lat(), e.latLng.lng());
    },
    [onChange],
  );

  const onMarkerDragEnd = useCallback(
    (e: google.maps.MapMouseEvent) => {
      if (e.latLng) onChange(e.latLng.lat(), e.latLng.lng());
    },
    [onChange],
  );

  if (loadError) {
    // Map SDK failed to load (bad key, network, restricted domain) — degrade to
    // the geolocation-only experience so checkout/address still work.
    return (
      <div className="location-picker">
        <div className="location-picker-fallback">
          <p className="add-error">
            The map couldn’t load. You can still pin your location:
          </p>
          <button
            type="button"
            className="btn btn-block"
            onClick={() => useCurrentLocation()}
            disabled={locating}
          >
            {locating ? 'Locating…' : '📍 Use my current location'}
          </button>
          {geoError && <span className="add-error">{geoError}</span>}
          <p className="location-readout muted">
            {coords
              ? `Pinned: ${coords.lat.toFixed(4)}, ${coords.lng.toFixed(4)}`
              : 'No location set yet.'}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="location-picker">
      <div className="location-picker-actions">
        <button
          type="button"
          className="btn btn-outline"
          onClick={() =>
            useCurrentLocation((la, ln) => {
              mapRef.current?.panTo({ lat: la, lng: ln });
            })
          }
          disabled={locating || !isLoaded}
        >
          {locating ? 'Locating…' : '📍 Use my current location'}
        </button>
      </div>
      {geoError && <span className="add-error">{geoError}</span>}
      {isLoaded ? (
        <GoogleMap
          mapContainerClassName="location-map"
          center={center}
          zoom={coords ? 16 : 13}
          onClick={onMapClick}
          onLoad={(map) => {
            mapRef.current = map;
          }}
          onUnmount={() => {
            mapRef.current = null;
          }}
          options={{
            streetViewControl: false,
            mapTypeControl: false,
            fullscreenControl: false,
            clickableIcons: false,
          }}
        >
          {coords && (
            <Marker
              position={coords}
              draggable
              onDragEnd={onMarkerDragEnd}
            />
          )}
        </GoogleMap>
      ) : (
        <div className="location-map location-map-loading">
          <span className="muted">Loading map…</span>
        </div>
      )}
      <p className="location-readout muted">
        {coords
          ? `Pinned: ${coords.lat.toFixed(4)}, ${coords.lng.toFixed(4)} · tap the map or drag the pin to adjust.`
          : 'Tap the map to drop a pin, or use your current location.'}
      </p>
    </div>
  );
}
