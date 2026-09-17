'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ApiError,
  cancelOrder,
  fetchOrderInvoice,
  getOrder,
  getStore,
  orderStreamUrl,
} from '@/app/lib/api';
import { formatDateTime, formatRupees } from '@/app/lib/format';
import { lineDisplayName } from '@/app/lib/productName';
import { useAuth } from '@/app/components/AuthProvider';
import { useCartActions } from '@/app/components/CartProvider';
import { clearLastOrder, loadLastOrder } from '@/app/lib/lastOrder';
import PushOptIn from '@/app/components/PushOptIn';
import type { Order, OrderStatus } from '@/app/lib/types';

// Display order + labels for the live status timeline (CANCELLED handled apart).
const STATUS_FLOW: OrderStatus[] = [
  'PLACED',
  'CONFIRMED',
  'PACKING',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

// Emoji shown in the big headline — reflects the LIVE status. The titles are
// translated via the 'order' namespace (see HEADLINE_KEY).
const STATUS_EMOJI: Record<OrderStatus, string> = {
  PLACED: '✅',
  CONFIRMED: '✅',
  PACKING: '📦',
  OUT_FOR_DELIVERY: '🛵',
  DELIVERY_FAILED: '⚠️',
  DELIVERED: '🎉',
  CANCELLED: '❌',
};

// Payment status reaches the client as the API's enum name (PAID, FAILED,
// COD_PENDING). Only render one this build has customer copy for: anything
// else shows the payment method alone, rather than dropping a raw
// "COD_PENDING" into the middle of the customer's order summary.
const PAYMENT_STATUS_KEYS = ['PAID', 'FAILED', 'COD_PENDING'] as const;
type KnownPaymentStatus = (typeof PAYMENT_STATUS_KEYS)[number];

function isKnownPaymentStatus(status: string): status is KnownPaymentStatus {
  return (PAYMENT_STATUS_KEYS as readonly string[]).includes(status);
}

const HEADLINE_KEY = {
  PLACED: 'headlinePlaced',
  CONFIRMED: 'headlineConfirmed',
  PACKING: 'headlinePacking',
  OUT_FOR_DELIVERY: 'headlineOutForDelivery',
  DELIVERY_FAILED: 'headlineDeliveryFailed',
  DELIVERED: 'headlineDelivered',
  CANCELLED: 'headlineCancelled',
} as const satisfies Record<OrderStatus, string>;

export default function OrderPage({ params }: { params: { id: string } }) {
  // The route param is the unguessable tracking token, not the numeric id.
  const trackingToken = params.id;
  const router = useRouter();
  const { reset } = useCartActions();
  const { isAuthenticated } = useAuth();
  const t = useTranslations('order');
  const ts = useTranslations('orderStatus');
  const tc = useTranslations('common');
  const tCheckout = useTranslations('checkout');
  const tPayment = useTranslations('paymentStatus');
  const locale = useLocale();

  // Orders are owner-scoped server-side: the token alone grants nothing, so a
  // signed-out visitor is sent to log in first (and brought back here). Auth
  // hydrates from localStorage after mount — wait a tick before gating.
  const [checked, setChecked] = useState(false);
  useEffect(() => setChecked(true), []);
  const loginUrl = `/account/login?next=${encodeURIComponent(`/order/${trackingToken}`)}`;
  useEffect(() => {
    if (checked && !isAuthenticated) {
      router.replace(loginUrl);
    }
  }, [checked, isAuthenticated, router, loginUrl]);

  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [invoiceBusy, setInvoiceBusy] = useState(false);
  const [invoiceError, setInvoiceError] = useState<string | null>(null);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  // The store's contact number, for the failures this page can't resolve on its
  // own (a cancellation that came too late is the customer's problem now, and
  // telling them so without telling them who to call is a dead end). Loaded
  // separately and best-effort: no number, or a failed fetch, just means the
  // notice stands on its own — it must never block the order from rendering.
  const [supportPhone, setSupportPhone] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false;
    getStore()
      .then((s) => {
        if (!cancelled) setSupportPhone(s.supportPhone?.trim() || null);
      })
      .catch(() => {
        /* best-effort — the notices below simply omit the call link */
      });
    return () => {
      cancelled = true;
    };
  }, []);
  // Ticks once a second while the self-cancel window is open so the countdown
  // and the button's visibility stay live.
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);
  // Once we've successfully loaded the order, clear the local cart exactly once.
  const cartCleared = useRef(false);
  // The numeric id (from the fetched order) keys the SSE stream.
  const streamId = order?.id ?? null;

  const applyOrder = useCallback(
    (next: Order) => {
      setOrder(next);
      setLastUpdated(Date.now());
      if (!cartCleared.current) {
        cartCleared.current = true;
        // Only the order the customer has *just* placed clears the local cart.
        // Checkout leaves the note naming the order it created (see
        // lib/lastOrder.ts), so matching it against this page's order is what
        // tells "you arrived here from checkout" apart from "you opened your
        // order history" or "you tapped a push notification hours later".
        //
        // Resetting unconditionally, as this used to, meant that merely
        // looking at an old order silently emptied the basket the customer was
        // building — and re-stamped the note, so the cart then greeted them
        // with "Your order is placed" instead of their shopping.
        if (loadLastOrder()?.token === next.trackingToken) reset();
      }
      // On EVERY update, not just the first: a cancellation can arrive later
      // over the live stream (the shop cancelled while this page was open) as
      // well as from the customer's own cancel button. Either way the note
      // that greets them in the cart as "Your order is placed — track it" is
      // now wrong, so drop it here, where the cancellation is first known.
      // CartEmptyState re-checks the server too, for the case where this page
      // was never open to see it.
      if (next.status === 'CANCELLED' && loadLastOrder()?.token === next.trackingToken) {
        clearLastOrder();
      }
    },
    [reset],
  );

  // Initial load — only once the login gate has settled, so a signed-out
  // visitor is redirected instead of burning a doomed 401 fetch.
  useEffect(() => {
    if (!checked || !isAuthenticated) return;
    let cancelled = false;
    getOrder(trackingToken)
      .then((o) => {
        if (!cancelled) applyOrder(o);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) {
          // Session expired and the refresh failed: sign in again, come back.
          router.replace(loginUrl);
          return;
        }
        setError(
          err instanceof ApiError && err.status === 404
            ? t('notFound')
            : t('couldNotLoad'),
        );
      });
    return () => {
      cancelled = true;
    };
  }, [checked, isAuthenticated, trackingToken, applyOrder, router, loginUrl, t]);

  // Live updates: subscribe to the order SSE stream (keyed by the resolved
  // numeric id) and ALSO poll by the unguessable tracking token as the reliable
  // fallback (ARCHITECTURE §3.8). The order SSE stream emits NAMED "status"
  // events, so we listen for them explicitly (es.onmessage only fires for
  // *unnamed* events and would miss them). Polling — not just onerror — is what
  // guarantees the status advances, since the connection can stay open and
  // silent. Runs once the order id is known; stops once the order is terminal.
  useEffect(() => {
    if (streamId == null) return;
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let es: EventSource | null = null;
    let stopped = false;

    const stop = () => {
      stopped = true;
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
      if (es) {
        es.close();
        es = null;
      }
    };
    const refetch = () => {
      if (stopped) return;
      getOrder(trackingToken)
        .then((o) => {
          applyOrder(o);
          if (o.status === 'DELIVERED' || o.status === 'CANCELLED') stop();
        })
        .catch(() => {
          /* transient; keep trying */
        });
    };

    // Steady polling fallback so the customer keeps getting updates even if SSE
    // never opens or stays silent. It runs unconditionally: the stream only ever
    // makes updates quicker, never the difference between working and not.
    pollTimer = setInterval(refetch, 6000);

    if (typeof EventSource !== 'undefined') {
      try {
        es = new EventSource(orderStreamUrl(String(streamId)));
        es.addEventListener('status', refetch); // backend pushes NAMED "status" events
        es.onmessage = refetch; // also handle any unnamed events
        // No onerror handler: the browser auto-reconnects an EventSource that
        // dropped, and the poll above covers the gap either way. Nothing is
        // shown to the customer about the transport, so there is no state to
        // keep in step with it.
      } catch {
        /* polling already covers updates */
      }
    }

    return stop;
  }, [streamId, trackingToken, applyOrder]);

  // Self-service cancel window (refund policy: within 1 minute of placing,
  // before packing). Server is the authority; this only drives the UI.
  const cancelWindowMs = 60_000;
  const placedAtMs = order ? Date.parse(order.placedAt) : Number.NaN;
  const cancelSecondsLeft = Number.isNaN(placedAtMs)
    ? 0
    : Math.max(0, Math.ceil((placedAtMs + cancelWindowMs - nowMs) / 1000));
  const cancelEligible =
    order != null &&
    (order.status === 'PLACED' || order.status === 'CONFIRMED') &&
    cancelSecondsLeft > 0;

  // Tick the countdown once a second while the window is open.
  useEffect(() => {
    if (!cancelEligible) return;
    const timer = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [cancelEligible]);

  async function onCancelOrder() {
    if (!order || cancelBusy) return;
    if (!window.confirm(t('cancelConfirm'))) return;
    setCancelBusy(true);
    setCancelError(null);
    try {
      const updated = await cancelOrder(trackingToken);
      applyOrder(updated);
    } catch (err) {
      setCancelError(
        err instanceof ApiError && err.status === 422
          ? t('cancelWindowClosed')
          : t('cancelFailed'),
      );
    } finally {
      setCancelBusy(false);
    }
  }

  // Authenticated fetch + browser-side save: the invoice endpoint is
  // owner-scoped, so a plain <a href> (which can't carry the Bearer) won't do.
  async function onDownloadInvoice() {
    if (invoiceBusy) return;
    setInvoiceBusy(true);
    setInvoiceError(null);
    try {
      const blob = await fetchOrderInvoice(trackingToken);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `townbasket-invoice-${order?.publicCode ?? trackingToken}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      // 422: no invoice for this order yet — either it isn't delivered (the
      // button is hidden then, so this is a race: it was cancelled or the
      // status moved in another tab) or it was cancelled outright.
      setInvoiceError(
        err instanceof ApiError && err.status === 422
          ? order?.status === 'CANCELLED'
            ? t('invoiceCancelled')
            : t('invoiceAfterDelivery')
          : t('invoiceFailed'),
      );
    } finally {
      setInvoiceBusy(false);
    }
  }

  // Login gate: don't render (or fetch) someone's order details until the
  // session has hydrated and the visitor is signed in.
  if (!checked) {
    return <p className="empty-state">{tc('loading')}</p>;
  }
  if (!isAuthenticated) {
    return (
      <div className="empty-state">
        <p>{t('signInPrompt')}</p>
        <Link href={loginUrl} className="btn">
          {tCheckout('signInContinue')}
        </Link>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state">
        <p className="notice error">{error}</p>
        <Link href="/" className="btn">
          {tc('backToShopping')}
        </Link>
      </div>
    );
  }

  if (!order) {
    return (
      <div aria-busy="true" aria-label={t('loadingOrder')}>
        <div className="skeleton-row" />
        <div className="skeleton-row" />
        <div className="skeleton-row" />
        <div className="skeleton-row" />
      </div>
    );
  }

  const cancelled = order.status === 'CANCELLED';
  const deliveryFailed = order.status === 'DELIVERY_FAILED';
  // The rider's reason from the latest failed attempt — shown to the customer
  // verbatim; nothing sensitive is ever written there.
  const failureReason = deliveryFailed
    ? [...order.timeline].reverse().find((e) => e.toStatus === 'DELIVERY_FAILED')?.note ?? null
    : null;
  // Why the order was cancelled, from the same place: the note staff typed on
  // the CANCELLED transition. Without it the screen said only that the order
  // "was cancelled", leaving the one question the customer actually has —
  // whether the shop ran out, or closed, or could not reach them — unanswered,
  // with their money involved.
  //
  // CUSTOMER_REQUEST is the API's reserved token for a self-service cancel
  // rather than a reason staff wrote (see TransitionRequest). That one is
  // translated; a staff reason is rendered verbatim, because it is their own
  // words about this order and nothing here can translate it.
  const cancelNote = cancelled
    ? [...order.timeline].reverse().find((e) => e.toStatus === 'CANCELLED')?.note ?? null
    : null;
  const cancelledByCustomer = cancelNote === 'CUSTOMER_REQUEST';
  const cancelReason = cancelledByCustomer ? null : cancelNote;
  const headlineEmoji = STATUS_EMOJI[order.status] ?? STATUS_EMOJI.PLACED;
  const headlineTitle = t(HEADLINE_KEY[order.status] ?? 'headlinePlaced');
  // A failed attempt sits beside the flow, not on it: the order DID reach
  // "out for delivery", so the timeline stays lit to that step while the
  // notice above it explains what happened next.
  const currentIndex = STATUS_FLOW.indexOf(
    deliveryFailed ? 'OUT_FOR_DELIVERY' : order.status,
  );
  // Map each status to the time it was reached, from the timeline.
  const reachedAt = new Map(order.timeline.map((t) => [t.toStatus, t.at]));

  return (
    <>
      <div className="order-confirm">
        <div className="big-emoji" aria-hidden>
          {headlineEmoji}
        </div>
        <h1>{headlineTitle}</h1>
        <p className="muted">
          {t('orderNumberTime', {
            code: order.publicCode,
            time: formatDateTime(order.placedAt, locale),
          })}
        </p>
        {cancelEligible && (
          <div className="cancel-window">
            <button
              type="button"
              className="btn btn-outline cancel-order-btn"
              onClick={onCancelOrder}
              disabled={cancelBusy}
            >
              {cancelBusy
                ? t('cancelling')
                : t('cancelOrderWithSeconds', { seconds: cancelSecondsLeft })}
            </button>
            <p className="muted cancel-window-hint">{t('cancelWindowHint')}</p>
          </div>
        )}
        {/* Both cancel failures leave the customer with something only the shop
            can resolve, so the notice carries the way to reach them. The copy
            itself no longer says "contact support": it used to, while the app
            carried no number anywhere, which sent people looking for a channel
            that did not exist. */}
        {cancelError && (
          <p className="notice error">
            {cancelError}
            {supportPhone && (
              <>
                {' '}
                <a href={`tel:${supportPhone.replace(/[^+\d]/g, '')}`}>
                  {t('callStore', { phone: supportPhone })}
                </a>
              </>
            )}
          </p>
        )}
      </div>

      {order.status === 'OUT_FOR_DELIVERY' && order.deliveryOtp && (
        <div className="otp-card">
          <span className="otp-label">{t('deliveryCode')}</span>
          <span className="otp-code">{order.deliveryOtp}</span>
          <span className="muted otp-hint">{t('deliveryCodeHintOnTheWay')}</span>
        </div>
      )}

      <section>
        <h2 className="section-title">
          {t('orderStatusHeading')}
          {lastUpdated && (
            <span className="muted" style={{ fontSize: '0.75rem', marginLeft: '0.5rem' }}>
              {t('lastUpdated', {
                time: formatDateTime(new Date(lastUpdated).toISOString(), locale),
              })}
            </span>
          )}
        </h2>

        {deliveryFailed && (
          <p className="notice warn" role="status">
            {t('deliveryFailedNotice')}
            {failureReason ? <> {t('deliveryFailedReason', { reason: failureReason })}</> : null}
          </p>
        )}
        {cancelled ? (
          <p className="notice error">
            {t('cancelledNotice')}
            {cancelledByCustomer ? <> {t('cancelledByYou')}</> : null}
            {cancelReason ? <> {t('cancelledReason', { reason: cancelReason })}</> : null}
          </p>
        ) : (
          <ol className="status-timeline">
            {STATUS_FLOW.map((status, i) => {
              const done = i <= currentIndex;
              const current = i === currentIndex;
              const at = reachedAt.get(status);
              return (
                <li
                  key={status}
                  className={`status-step ${done ? 'done' : ''} ${current ? 'current' : ''}`}
                >
                  <span className="status-marker" aria-hidden>
                    {done ? '✓' : ''}
                  </span>
                  <span className="status-text">
                    <span className="status-name">{ts(status)}</span>
                    {at && <span className="muted status-at">{formatDateTime(at, locale)}</span>}
                  </span>
                </li>
              );
            })}
          </ol>
        )}
        {/* Offer push only while the order is still in flight — there is
            nothing left to notify about once it is delivered or cancelled. */}
        {!cancelled && order.status !== 'DELIVERED' && <PushOptIn />}
      </section>

      <section>
        <h2 className="section-title">{t('orderSummary')}</h2>
        <ul className="order-items">
          {order.items.map((item, idx) => (
            <li key={idx} className="order-item-row">
              <span>
                {lineDisplayName(item, locale)}{' '}
                <span className="muted">
                  {t('itemLine', { label: item.label, qty: item.qty })}
                </span>
              </span>
              <span>{formatRupees(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
        <div className="cart-summary">
          <div className="cart-summary-row">
            <span>{t('subtotal')}</span>
            <span>{formatRupees(order.subtotal)}</span>
          </div>
          <div className="cart-summary-row total">
            <span>{t('total')}</span>
            <strong>{formatRupees(order.total)}</strong>
          </div>
          {order.totalTax > 0 && (
            <div className="cart-summary-row">
              <span className="muted">
                {t('includesGst', { amount: formatRupees(order.totalTax) })}
              </span>
            </div>
          )}
          <div className="cart-summary-row">
            <span>{t('payment')}</span>
            <span>
              {order.paymentMethod === 'COD' ? tCheckout('cod') : 'UPI'}
              {isKnownPaymentStatus(order.paymentStatus)
                ? ` · ${tPayment(order.paymentStatus)}`
                : ''}
            </span>
          </div>
        </div>
        {/* A GST invoice records a supply that has happened, so the server
            issues one only after handover. Offer the download once the order is
            delivered (or once an invoice already exists), say when it will
            appear while the order is still in flight, and say nothing at all
            for a cancelled order — there will never be one. */}
        {order.status === 'DELIVERED' || order.invoiceNumber ? (
          <button
            type="button"
            className="btn btn-outline btn-block"
            onClick={onDownloadInvoice}
            disabled={invoiceBusy}
            style={{ marginTop: '0.75rem' }}
          >
            {invoiceBusy ? t('invoicePreparing') : t('downloadInvoice')}
          </button>
        ) : cancelled ? null : (
          <p className="muted" style={{ fontSize: '0.8rem', marginTop: '0.75rem' }}>
            {t('invoiceAfterDelivery')}
          </p>
        )}
        {invoiceError && <p className="notice error">{invoiceError}</p>}
      </section>

      <section className="order-address">
        <h2 className="section-title">{t('deliveringTo')}</h2>
        <p>
          <strong>{order.customerName}</strong> · {order.phone}
          <br />
          {order.address.line}
        </p>
      </section>

      <Link href="/" className="btn btn-outline btn-block">
        {tc('continueShopping')}
      </Link>
    </>
  );
}
