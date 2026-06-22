'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError, checkServiceability, getStore, placeOrder } from '@/app/lib/api';
import { formatRupees, subtractRupees } from '@/app/lib/format';
import { loadServiceability, saveServiceability } from '@/app/lib/serviceability';
import { useCart } from '@/app/components/CartProvider';
import type { PaymentMethod } from '@/app/lib/types';

export default function CheckoutPage() {
  const router = useRouter();
  const { cart, refresh } = useCart();

  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [line, setLine] = useState('');
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');

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

  const items = cart?.items ?? [];
  const subtotal = cart?.subtotal ?? 0;
  const belowMin = minOrderValue != null && subtotal < minOrderValue;
  const hasUnavailable = items.some((i) => !i.available);
  // Surface a stock shortage before the final tap, not only at reservation time.
  const hasShortage = items.some((i) => i.available && i.availableStock < i.qty);

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
    !hasShortage &&
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
          expectedTotal: subtotal,
        },
        idempotencyKey.current,
      );

      // Track by the unguessable token (never the sequential id). The order page
      // clears the local cart once it loads successfully.
      router.push(`/order/${order.trackingToken}`);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          // Stock conflict: an item sold out (or its quantity is no longer
          // available) since the cart was loaded.
          setError(
            'Some items just sold out or stock changed. Please review your cart and try again.',
          );
          await refresh();
        } else if (err.status === 422 || err.status === 400) {
          // Business rule: below minimum, item unavailable, store closed, or the
          // total changed since you confirmed it. Refresh so the cart reflects it.
          setError(
            'We couldn’t place this order — the store may be closed, an item may be unavailable, the total may have changed, or you’re below the minimum. Please review your cart and retry.',
          );
          await refresh();
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
              {formatRupees(subtractRupees(minOrderValue, subtotal))} more to checkout.
            </p>
          )}
          {hasUnavailable && (
            <p className="notice error">
              Some items are no longer available. Please{' '}
              <Link href="/cart">edit your cart</Link> first.
            </p>
          )}
          {hasShortage && !hasUnavailable && (
            <p className="notice error">
              Some items don’t have enough stock for the quantity you chose.
              Please <Link href="/cart">edit your cart</Link> first.
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
