'use client';

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  ApiError,
  AuthRequiredError,
  closeStoreForToday,
  getStoreSettings,
  reopenStore,
  updateStoreSettings,
} from '@/app/lib/api';
import type { StoreSettings } from '@/app/lib/types';
import { useAuth } from './AuthProvider';
import { ListSkeleton } from './Skeleton';

/** "08:00:00" -> "08:00" for <input type="time">. */
function hhmm(t: string): string {
  return t.slice(0, 5);
}

function fmtClock(t: string): string {
  const [h, m] = t.split(':').map(Number);
  const d = new Date();
  d.setHours(h, m, 0, 0);
  return d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

/**
 * Store settings: hours, minimum order, delivery radius, location — and the
 * one switch that matters on a bad day, "Close for today". Until this panel,
 * every one of these lived only in SQL.
 *
 * The status card at the top shows what CUSTOMERS currently see (open / closed
 * by hours / closed manually), decided on the server clock, so staff can
 * confirm a change took effect without opening the storefront.
 */
export default function StorePanel() {
  const { refresh: refreshAuth } = useAuth();
  const [store, setStore] = useState<StoreSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Form state mirrors the card; submit sends the whole card.
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [opening, setOpening] = useState('08:00');
  const [closing, setClosing] = useState('21:00');
  const [radiusKm, setRadiusKm] = useState('5');
  const [minOrder, setMinOrder] = useState('299');
  const [supportPhone, setSupportPhone] = useState('');

  const hydrate = useCallback((s: StoreSettings) => {
    setStore(s);
    setName(s.name);
    setAddress(s.address);
    setLat(String(s.lat));
    setLng(String(s.lng));
    setOpening(hhmm(s.openingTime));
    setClosing(hhmm(s.closingTime));
    setRadiusKm(String(s.deliveryRadiusMeters / 1000));
    setMinOrder(String(s.minOrderValue));
    setSupportPhone(s.supportPhone ?? '');
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      hydrate(await getStoreSettings());
    } catch (err) {
      if (err instanceof AuthRequiredError) { refreshAuth(); return; }
      setError('Could not load the store settings.');
    } finally {
      setLoading(false);
    }
  }, [hydrate, refreshAuth]);

  useEffect(() => { void load(); }, [load]);

  function fail(err: unknown, fallback: string) {
    if (err instanceof AuthRequiredError) { refreshAuth(); return; }
    if (err instanceof ApiError && (err.status === 400 || err.status === 422)) {
      setError(err.message || fallback);
    } else {
      setError(fallback);
    }
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null); setSuccess(null);
    try {
      const updated = await updateStoreSettings({
        name: name.trim(),
        address: address.trim(),
        lat: Number(lat),
        lng: Number(lng),
        deliveryRadiusMeters: Math.round(Number(radiusKm) * 1000),
        openingTime: opening,
        closingTime: closing,
        minOrderValue: Number(minOrder),
        supportPhone: supportPhone.trim(),
      });
      hydrate(updated);
      setSuccess('Store settings saved. Customers see the new hours immediately.');
    } catch (err) {
      fail(err, 'Could not save. Check the values and try again.');
    } finally {
      setBusy(false);
    }
  }

  async function onCloseToday() {
    const reason = window.prompt(
      'Close the store for the rest of today?\n\nReason shown to customers (optional):',
      '',
    );
    if (reason === null) return;
    setBusy(true); setError(null); setSuccess(null);
    try {
      hydrate(await closeStoreForToday(reason.trim()));
      setSuccess('Closed for today. The storefront now shows the closed banner and checkout is blocked; it reopens automatically tomorrow.');
    } catch (err) {
      fail(err, 'Could not close the store.');
    } finally {
      setBusy(false);
    }
  }

  async function onReopen() {
    setBusy(true); setError(null); setSuccess(null);
    try {
      hydrate(await reopenStore());
      setSuccess('Reopened. Normal trading hours apply again.');
    } catch (err) {
      fail(err, 'Could not reopen the store.');
    } finally {
      setBusy(false);
    }
  }

  const valid =
    name.trim() && address.trim() &&
    Number.isFinite(Number(lat)) && Number.isFinite(Number(lng)) &&
    /^\d{2}:\d{2}$/.test(opening) && /^\d{2}:\d{2}$/.test(closing) && opening !== closing &&
    Number(radiusKm) >= 0.5 && Number(radiusKm) <= 50 &&
    Number(minOrder) >= 0;

  if (loading && !store) return <ListSkeleton label="Loading store settings…" rows={4} />;
  if (!store) return <p className="order-error">{error ?? 'No store configured.'}</p>;

  const status = store.manuallyClosed
    ? { cls: 'closed-manual', label: 'Closed for today', detail: store.closedReason ? `Reason: ${store.closedReason}` : 'No reason given' }
    : store.open
      ? { cls: 'open', label: 'Open now', detail: `Trading until ${fmtClock(store.closingTime)}` }
      : { cls: 'closed-hours', label: 'Closed (outside hours)', detail: `Opens ${store.opensNextDay ? 'tomorrow' : 'today'} at ${fmtClock(store.openingTime)}` };

  return (
    <section className="inv-panel">
      <div className="inv-panel-head">
        <h2 className="cat-panel-title">Store</h2>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          What customers see is decided on the server clock (IST).
        </span>
      </div>

      <div className={`store-status ${status.cls}`} role="status">
        <div>
          <strong>{status.label}</strong>
          <span className="muted"> · {status.detail}</span>
        </div>
        {store.manuallyClosed ? (
          <button type="button" className="btn" disabled={busy} onClick={onReopen}>
            {busy ? '…' : 'Reopen now'}
          </button>
        ) : (
          <button type="button" className="btn btn-ghost danger" disabled={busy} onClick={onCloseToday}>
            {busy ? '…' : 'Close for today…'}
          </button>
        )}
      </div>

      {error && <p className="order-error">{error}</p>}
      {success && <p className="account-banner ok">{success}</p>}

      <form className="rider-form store-form" onSubmit={onSave}>
        <label className="login-field">
          Store name
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="login-field store-form-wide">
          Address
          <input type="text" value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label className="login-field">
          Opens at
          <input type="time" value={opening} onChange={(e) => setOpening(e.target.value)} />
        </label>
        <label className="login-field">
          Closes at
          <input type="time" value={closing} onChange={(e) => setClosing(e.target.value)} />
        </label>
        <label className="login-field">
          Minimum order (₹)
          <input type="number" min={0} step={1} value={minOrder} onChange={(e) => setMinOrder(e.target.value)} />
        </label>
        <label className="login-field">
          Delivery radius (km)
          <input type="number" min={0.5} max={50} step={0.5} value={radiusKm} onChange={(e) => setRadiusKm(e.target.value)} />
        </label>
        <label className="login-field store-form-wide">
          Contact number for customers
          <input
            type="tel"
            value={supportPhone}
            onChange={(e) => setSupportPhone(e.target.value)}
            placeholder="e.g. 0821 234 5678"
          />
        </label>
        <label className="login-field">
          Latitude
          <input type="number" step="any" value={lat} onChange={(e) => setLat(e.target.value)} />
        </label>
        <label className="login-field">
          Longitude
          <input type="number" step="any" value={lng} onChange={(e) => setLng(e.target.value)} />
        </label>
        <button type="submit" className="btn" disabled={!valid || busy}>
          {busy ? 'Saving…' : 'Save settings'}
        </button>
      </form>
      <p className="muted" style={{ fontSize: '0.85rem' }}>
        Hours are store-local (IST). The closed banner appears on every storefront page within
        about five minutes without a reload; checkout blocks immediately.
      </p>
      <p className="muted" style={{ fontSize: '0.85rem' }}>
        The contact number is shown to customers as a tap-to-call link — in the footer, and
        wherever the app asks them to get in touch (a cancellation that came too late, for
        instance). Leave it empty and the app stops offering to put them through, so a number
        here is what makes that advice worth giving.
      </p>
    </section>
  );
}
