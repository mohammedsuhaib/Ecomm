'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ApiError,
  checkServiceability,
  getPaymentMethods,
  getStore,
  listAddresses,
  placeOrder,
} from '@/app/lib/api';
import { formatRupees, subtractRupees } from '@/app/lib/format';
import { loadServiceability, saveServiceability } from '@/app/lib/serviceability';
import { useCart } from '@/app/components/CartProvider';
import { useAuth } from '@/app/components/AuthProvider';
import LocationPicker from '@/app/components/LocationPickerLazy';
import { CheckoutSkeleton } from '@/app/components/Skeleton';
import PriceChangeNotice from '@/app/components/PriceChangeNotice';
import type { PaymentMethod, SavedAddress } from '@/app/lib/types';

/**
 * Whether two coordinates are the same place. 1e-6 degrees is roughly 10 cm —
 * far below the gap between any two real addresses, and well above the noise
 * from formatting a float through a string and back.
 */
function sameCoord(a: number, b: number): boolean {
  return Number.isFinite(a) && Number.isFinite(b) && Math.abs(a - b) < 1e-6;
}

export default function CheckoutPage() {
  const t = useTranslations('checkout');
  const tc = useTranslations('common');
  const router = useRouter();
  const { cart, refresh, priceChanges } = useCart();
  const { user, isAuthenticated } = useAuth();
  // Auth hydrates from localStorage after mount; wait a tick before gating so a
  // logged-in customer isn't bounced. Placing an order requires login.
  const [checked, setChecked] = useState(false);

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
  // Set once the customer decides the address themselves — typing a line,
  // moving the map pin, or picking a saved address. The saved-address prefill
  // below arrives from the network, so "the field is still empty" is not a
  // reliable test for "untouched"; this is.
  const addressTouched = useRef(false);

  const [minOrderValue, setMinOrderValue] = useState<number | null>(null);

  // Server-decided (its clock, not the device's): the shop is shut, so the

  // order would be refused. Say so up front instead of at submit time.

  const [storeClosed, setStoreClosed] = useState(false);

  // Only methods the server accepts are offered, so a customer can never

  // pick one that checkout would refuse. COD until the server says more.

  const [methods, setMethods] = useState<PaymentMethod[]>(['COD']);
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

  // Placing an order requires a logged-in (OTP-verified) account — no guest
  // checkout. Once hydration settles, send guests to sign in and bring them back
  // here afterward (their cart merges into the account on login).
  useEffect(() => setChecked(true), []);
  useEffect(() => {
    if (checked && !isAuthenticated) {
      router.replace('/account/login?next=/checkout');
    }
  }, [checked, isAuthenticated, router]);

  useEffect(() => {
    getStore({ noStore: true })
      .then((s) => {
        setMinOrderValue(s.minOrderValue);
        // Strictly false only — see StoreClosedBanner: a missing field
        // (older API) must not block a customer who could order.
        setStoreClosed(s.open === false);
      })
      .catch(() => {
        setMinOrderValue(null);
        // Unknown status: let the server be the judge rather than blocking a
        // customer who could have ordered.
        setStoreClosed(false);
      });
  }, []);

  useEffect(() => {
    getPaymentMethods()
      .then((res) => {
        const allowed = res.methods?.length ? res.methods : (['COD'] as PaymentMethod[]);
        setMethods(allowed);
        // If the selected method just became unavailable, fall back to COD
        // rather than leaving a dead selection the server would reject.
        setPaymentMethod((current) =>
          allowed.includes(current) ? current : allowed[0],
        );
      })
      .catch(() => setMethods(['COD']));
  }, []);

  // Fallback pin: wherever the LocationGate last checked. This is all a guest
  // (or a customer with no saved addresses) has to go on. A saved default
  // address supersedes it once the address list arrives — see below.
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
    // Last 10 digits, not the raw value: a profile written before phones were
    // canonicalised holds E.164 (+919632500797), which fails the 10-digit check
    // below and would leave Place Order dead with no visible reason.
    if (user.phone) setPhone(user.phone.replace(/\D/g, '').slice(-10));
  }, [isAuthenticated, user]);

  useEffect(() => {
    if (!isAuthenticated) {
      setSavedAddresses([]);
      return;
    }
    listAddresses()
      .then((list) => {
        setSavedAddresses(list);
        // Pre-pick the default address, line AND pin together.
        //
        // These must move as one: the LocationGate's coordinates are prefilled
        // synchronously on mount, so a per-field "only if empty" guard kept
        // those and paired them with this address's text — a form whose label
        // read "221B Kuvempunagar" while its pin sat wherever the browser
        // happened to be. The rider navigates by the pin, so that is a delivery
        // to the wrong place, and it also left the default's chip unhighlighted
        // (the chip is "selected" only when line and pin both match it).
        //
        // A saved default is a deliberate choice and outranks the gate's guess;
        // only the customer's own edit (addressTouched) outranks the default.
        const def = list.find((a) => a.isDefault) ?? list[0];
        if (def && !addressTouched.current) {
          setLine(def.line);
          setLat(String(def.lat));
          setLng(String(def.lng));
        }
      })
      .catch(() => setSavedAddresses([]));
  }, [isAuthenticated]);

  function pickAddress(a: SavedAddress) {
    addressTouched.current = true;
    setLine(a.line);
    setLat(String(a.lat));
    setLng(String(a.lng));
  }

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
    !storeClosed &&
    // expectedTotal is only a real guard if the prices behind it are ones the
    // customer accepted; until then there is nothing safe to submit.
    priceChanges.length === 0 &&
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
        setError(t('notServiceable', { store: svc.storeName }));
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
          // available) since the cart was loaded. Refresh so the cart reflects it.
          setError(t('errorStock'));
          await refresh();
        } else if (err.status === 422 || err.status === 400) {
          // Business rule: below minimum, item unavailable, store closed, or the
          // total changed since you confirmed it. Re-read the store so a
          // closure that started mid-checkout is named instead of hidden behind
          // the catch-all, then refresh the cart.
          const closedNow = await getStore({ noStore: true })
            .then((st) => st.open === false)
            .catch(() => false);
          setStoreClosed(closedNow);
          setError(closedNow ? t('errorStoreClosed') : t('errorInvalid'));
          await refresh();
        } else {
          setError(t('errorGeneric'));
        }
      } else {
        setError(t('errorGeneric'));
      }
      setSubmitting(false);
    }
  }

  // Gate: login required to check out (no guest checkout). The check runs on
  // the client, so this is the first frame the customer sees — a skeleton that
  // holds the form's shape rather than one line of text that the real page then
  // shoves off the screen.
  if (!checked) {
    return <CheckoutSkeleton label={tc('loading')} />;
  }
  if (!isAuthenticated) {
    return (
      <div className="empty-state">
        <p>{t('signInPrompt')}</p>
        <Link href="/account/login?next=/checkout" className="btn">
          {t('signInContinue')}
        </Link>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <>
        <nav className="breadcrumb">
          <Link href="/cart">{tc('cart')}</Link> / <span>{tc('checkout')}</span>
        </nav>
        <div className="empty-state">
          <p>{t('emptyCart')}</p>
          <Link href="/" className="btn">
            {tc('startShopping')}
          </Link>
        </div>
      </>
    );
  }

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/cart">{tc('cart')}</Link> / <span>{tc('checkout')}</span>
      </nav>

      <h1 className="section-title" style={{ marginTop: 0 }}>
        {t('title')}
      </h1>

      {error && <p className="notice error">{error}</p>}

      <form className="checkout-form" onSubmit={onSubmit}>
        <fieldset className="checkout-section" disabled={submitting}>
          <legend>{t('deliveryDetails')}</legend>
          <div className="field">
            <label htmlFor="name">{t('fullName')}</label>
            <input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="phone">{t('phoneNumber')}</label>
            <input
              id="phone"
              inputMode="numeric"
              placeholder={t('phonePlaceholder')}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              autoComplete="tel"
              required
            />
            {phone && !phoneValid && (
              <span className="add-error">{t('phoneError')}</span>
            )}
          </div>
          {savedAddresses.length > 0 && (
            <div className="field">
              {/* Not a <label>: it wraps no control (a11y dead label). */}
              <span className="field-label">{t('savedAddresses')}</span>
              <div className="saved-address-picks">
                {savedAddresses.map((a) => {
                  // Compared numerically, not as text: coordinates round-trip
                  // through strings and the map picker, so "12.22" vs
                  // "12.220000000000001" is the same doorstep but would fail an
                  // exact string match and leave the chip looking unselected.
                  const active =
                    line.trim() === a.line.trim() &&
                    sameCoord(a.lat, latNum) &&
                    sameCoord(a.lng, lngNum);
                  return (
                    <button
                      key={a.id}
                      type="button"
                      className={`address-chip ${active ? 'active' : ''}`}
                      onClick={() => pickAddress(a)}
                    >
                      <span className="chip-label">
                        {a.label || t('addressFallback')}
                        {a.isDefault ? ` · ${tc('default')}` : ''}
                      </span>
                      <span className="chip-line muted">{a.line}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
          <div className="field">
            <label htmlFor="line">{t('deliveryAddress')}</label>
            <textarea
              id="line"
              rows={3}
              placeholder={t('addressPlaceholder')}
              value={line}
              onChange={(e) => {
                addressTouched.current = true;
                setLine(e.target.value);
              }}
              required
            />
          </div>
          <div className="field">
            {/* Not a <label>: it wraps no control (a11y dead label). */}
            <span className="field-label">{t('deliveryLocation')}</span>
            <LocationPicker
              lat={lat}
              lng={lng}
              onChange={(la, ln) => {
                addressTouched.current = true;
                setLat(String(la));
                setLng(String(ln));
              }}
            />
          </div>
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            {t('mapHint')}
          </p>
        </fieldset>

        <fieldset className="checkout-section" disabled={submitting}>
          <legend>{t('paymentMethod')}</legend>
          <label className="radio-row">
            <input
              type="radio"
              name="payment"
              value="COD"
              checked={paymentMethod === 'COD'}
              onChange={() => setPaymentMethod('COD')}
            />
            <span>
              <strong>{t('cod')}</strong>
              <br />
              <span className="muted">{t('codHint')}</span>
            </span>
          </label>
          {methods.includes('UPI') ? (
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
                <span className="muted">{t('upiHint')}</span>
              </span>
            </label>
          ) : (
            <p className="muted upi-soon">{t('upiComingSoon')}</p>
          )}
        </fieldset>

        <div className="cart-summary">
          <div className="cart-summary-row">
            <span>{t('subtotal')}</span>
            <strong>{formatRupees(subtotal)}</strong>
          </div>
          {belowMin && minOrderValue != null && (
            <p className="notice warn">
              {t('minOrderNotice', {
                min: formatRupees(minOrderValue),
                needed: formatRupees(subtractRupees(minOrderValue, subtotal)),
              })}
            </p>
          )}
          {hasUnavailable && (
            <p className="notice error">
              {t.rich('someNoLongerAvailableEdit', {
                link: (chunks) => <Link href="/cart">{chunks}</Link>,
              })}
            </p>
          )}
          {hasShortage && !hasUnavailable && (
            <p className="notice error">
              {t.rich('someShortageEdit', {
                link: (chunks) => <Link href="/cart">{chunks}</Link>,
              })}
            </p>
          )}
          <PriceChangeNotice />
          <button
            type="submit"
            className="btn btn-block"
            disabled={!formValid || submitting}
          >
            {submitting
              ? t('placingOrder')
              : t('placeOrder', { amount: formatRupees(subtotal) })}
          </button>
        </div>
      </form>
    </>
  );
}
