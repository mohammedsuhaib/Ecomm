'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ApiError,
  checkServiceability,
  getStore,
  listAddresses,
  placeOrder,
} from '@/app/lib/api';
import { formatRupees } from '@/app/lib/format';
import { loadServiceability, saveServiceability } from '@/app/lib/serviceability';
import { useCart } from '@/app/components/CartProvider';
import { useAuth } from '@/app/components/AuthProvider';
import type { PaymentMethod, SavedAddress } from '@/app/lib/types';

export default function CheckoutPage() {
  const router = useRouter();
  const { cart, refresh } = useCart();
  const { user, isAuthenticated } = useAuth();

  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [line, setLine] = useState('');
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');

  // Logged-in extras: prefill name/phone and offer saved addresses as quick
  // picks. Guests see none of this and the flow is unchanged.
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  // Track whether the name/phone have been touched so we don't clobber typing.
  const prefilled = useRef(false);

  const [minOrderValue, setMinOrderValue] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // One idempotency key per checkout attempt, reused across retries so a
  // flaky-network resubmit can't create a duplicate order (ARCHITECTURE §3.5).
  const idempotencyKey = useRef<string>('');
  if (!idempotencyKey.current) {
    idempotencyKey.current =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    getStore()
      .then((s) => setMinOrderValue(s.minOrderValue))
      .catch(() => setMinOrderValue(null));
  }, []);

  // Prefill the delivery coordinates from the location the LocationGate captured.
  useEffect(() => {
    const stored = loadServiceability();
    if (stored) {
      setLat(String(stored.lat));
      setLng(String(stored.lng));
    }
  }, []);

  // When logged in, prefill name/phone from the profile (once) and load saved
  // addresses for quick-pick. Guests skip this entirely.
  useEffect(() => {
    if (!isAuthenticated || !user || prefilled.current) return;
    prefilled.current = true;
    if (user.name) setName(user.name);
    if (user.phone) setPhone(user.phone);
  }, [isAuthenticated, user]);

  useEffect(() => {
    if (!isAuthenticated) {
      setSavedAddresses([]);
      return;
    }
    listAddresses()
      .then((list) => {
        setSavedAddresses(list);
        // Pre-pick the default address if the form is still empty.
        const def = list.find((a) => a.isDefault) ?? list[0];
        if (def) {
          setLine((prev) => (prev.trim() ? prev : def.line));
          setLat((prev) => (prev.trim() ? prev : String(def.lat)));
          setLng((prev) => (prev.trim() ? prev : String(def.lng)));
        }
      })
      .catch(() => setSavedAddresses([]));
  }, [isAuthenticated]);

  function pickAddress(a: SavedAddress) {
    setLine(a.line);
    setLat(String(a.lat));
    setLng(String(a.lng));
  }

  const items = cart?.items ?? [];
  const subtotal = cart?.subtotal ?? 0;
  const belowMin = minOrderValue != null && subtotal < minOrderValue;
  const hasUnavailable = items.some((i) => !i.available);

  const phoneValid = useMemo(() => /^[0-9]{10}$/.test(phone.trim()), [phone]);
  const latNum = Number.parseFloat(lat);
  const lngNum = Number.parseFloat(lng);
  const coordsValid =
    !Number.isNaN(latNum) &&
    !Number.isNaN(lngNum) &&
    latNum >= -90 &&
    latNum <= 90 &&
    lngNum >= -180 &&
    lngNum <= 180;

  const formValid =
    items.length > 0 &&
    !belowMin &&
    !hasUnavailable &&
    name.trim().length > 1 &&
    phoneValid &&
    line.trim().length > 3 &&
    coordsValid;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!cart || !formValid || submitting) return;
    setSubmitting(true);
    setError(null);

    try {
      // Re-verify serviceability at checkout (ARCHITECTURE §3.7).
      const svc = await checkServiceability(latNum, lngNum);
      saveServiceability(svc, latNum, lngNum);
      if (!svc.serviceable) {
        setError(
          `Sorry, ${svc.storeName} doesn’t deliver to this address yet (outside the delivery range). Please choose a different location.`,
        );
        setSubmitting(false);
        return;
      }

      const order = await placeOrder(
        {
          cartId: cart.cartId,
          customerName: name.trim(),
          phone: phone.trim(),
          address: { line: line.trim(), lat: latNum, lng: lngNum },
          paymentMethod,
        },
        idempotencyKey.current,
      );

      // The order page clears the local cart once it loads successfully.
      router.push(`/order/${order.id}`);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setError(
            'Some items just went out of stock. Please review your cart and try again.',
          );
        } else if (err.status === 422 || err.status === 400) {
          setError(
            'We couldn’t place this order — your cart may be below the minimum or an item is unavailable. Please review and retry.',
          );
        } else {
          setError('Something went wrong placing your order. Please try again.');
        }
      } else {
        setError('Something went wrong placing your order. Please try again.');
      }
      setSubmitting(false);
    }
  }

  if (items.length === 0) {
    return (
      <>
        <nav className="breadcrumb">
          <Link href="/cart">Cart</Link> / <span>Checkout</span>
        </nav>
        <div className="empty-state">
          <p>Your cart is empty.</p>
          <Link href="/" className="btn">
            Start shopping
          </Link>
        </div>
      </>
    );
  }

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/cart">Cart</Link> / <span>Checkout</span>
      </nav>

      <h1 className="section-title" style={{ marginTop: 0 }}>
        Checkout
      </h1>

      {error && <p className="notice error">{error}</p>}

      <form className="checkout-form" onSubmit={onSubmit}>
        <fieldset className="checkout-section" disabled={submitting}>
          <legend>Delivery details</legend>
          <div className="field">
            <label htmlFor="name">Full name</label>
            <input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="phone">Phone number</label>
            <input
              id="phone"
              inputMode="numeric"
              placeholder="10-digit mobile"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              autoComplete="tel"
              required
            />
            {phone && !phoneValid && (
              <span className="add-error">Enter a 10-digit phone number.</span>
            )}
          </div>
          {savedAddresses.length > 0 && (
            <div className="field">
              <label>Saved addresses</label>
              <div className="saved-address-picks">
                {savedAddresses.map((a) => {
                  const active =
                    line.trim() === a.line.trim() &&
                    String(a.lat) === lat &&
                    String(a.lng) === lng;
                  return (
                    <button
                      key={a.id}
                      type="button"
                      className={`address-chip ${active ? 'active' : ''}`}
                      onClick={() => pickAddress(a)}
                    >
                      <span className="chip-label">
                        {a.label || 'Address'}
                        {a.isDefault ? ' · Default' : ''}
                      </span>
                      <span className="chip-line muted">{a.line}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
          <div className="field">
            <label htmlFor="line">Delivery address</label>
            <textarea
              id="line"
              rows={3}
              placeholder="Flat / house no, building, street, area, landmark"
              value={line}
              onChange={(e) => setLine(e.target.value)}
              required
            />
          </div>
          <div className="checkout-coords">
            <div className="field">
              <label htmlFor="lat">Latitude</label>
              <input
                id="lat"
                inputMode="decimal"
                value={lat}
                onChange={(e) => setLat(e.target.value)}
                required
              />
            </div>
            <div className="field">
              <label htmlFor="lng">Longitude</label>
              <input
                id="lng"
                inputMode="decimal"
                value={lng}
                onChange={(e) => setLng(e.target.value)}
                required
              />
            </div>
          </div>
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            Prefilled from your delivery location. We re-check that you’re within
            range before placing the order.
          </p>
        </fieldset>

        <fieldset className="checkout-section" disabled={submitting}>
          <legend>Payment method</legend>
          <label className="radio-row">
            <input
              type="radio"
              name="payment"
              value="COD"
              checked={paymentMethod === 'COD'}
              onChange={() => setPaymentMethod('COD')}
            />
            <span>
              <strong>Cash on Delivery</strong>
              <br />
              <span className="muted">Pay in cash when your order arrives.</span>
            </span>
          </label>
          <label className="radio-row">
            <input
              type="radio"
              name="payment"
              value="UPI"
              checked={paymentMethod === 'UPI'}
              onChange={() => setPaymentMethod('UPI')}
            />
            <span>
              <strong>UPI</strong>
              <br />
              <span className="muted">Pay online via UPI.</span>
            </span>
          </label>
        </fieldset>

        <div className="cart-summary">
          <div className="cart-summary-row">
            <span>Subtotal</span>
            <strong>{formatRupees(subtotal)}</strong>
          </div>
          {belowMin && minOrderValue != null && (
            <p className="notice warn">
              Minimum order is {formatRupees(minOrderValue)}. Add{' '}
              {formatRupees(minOrderValue - subtotal)} more to checkout.
            </p>
          )}
          {hasUnavailable && (
            <p className="notice error">
              Some items are out of stock. Please{' '}
              <Link href="/cart">edit your cart</Link> first.
            </p>
          )}
          <button
            type="submit"
            className="btn btn-block"
            disabled={!formValid || submitting}
          >
            {submitting
              ? 'Placing order…'
              : `Place order · ${formatRupees(subtotal)}`}
          </button>
        </div>
      </form>
    </>
  );
}
