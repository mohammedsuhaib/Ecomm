'use client';

import { useState } from 'react';
import { ApiError, AuthRequiredError, confirmDelivery, reportDeliveryFailure } from '@/app/lib/api';
import type { Order } from '@/app/lib/types';
import { useAuth } from './AuthProvider';

interface Props {
  order: Order;
  onDelivered: (id: string) => void;
}

function fmtTime(iso: string) {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
}

function fmtAmount(n: number) {
  return '₹' + n.toLocaleString('en-IN');
}

// Fixed reasons: a rider on the road should tap, not type, and staff want
// comparable data. "Other" still exists so nothing is forced into a wrong bucket.
const FAIL_REASONS = [
  'Customer not reachable',
  'Wrong or incomplete address',
  'Customer refused the order',
  'Customer asked to deliver later',
  'Other',
] as const;

export default function DeliveryCard({ order, onDelivered }: Props) {
  const { refresh } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  // "Can't deliver": pick a fixed reason, confirm, and the order leaves this
  // queue for staff to re-dispatch or cancel. Goods go back to the store.
  const [failing, setFailing] = useState(false);
  const [failReason, setFailReason] = useState('');
  const [reported, setReported] = useState(false);

  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${order.address.lat},${order.address.lng}`;

  async function submitOtp() {
    if (!otp.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await confirmDelivery(order.id, otp.trim());
      setDone(true);
      setTimeout(() => onDelivered(order.id), 1200);
    } catch (err) {
      if (err instanceof AuthRequiredError) { refresh(); return; }
      if (err instanceof ApiError && err.status === 422) {
        setError('Wrong OTP. Ask the customer to check their confirmation code.');
      } else {
        setError('Could not confirm delivery. Try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function submitFailure() {
    if (!failReason || busy) return;
    setBusy(true);
    setError(null);
    try {
      await reportDeliveryFailure(order.id, failReason);
      setReported(true);
      setTimeout(() => onDelivered(order.id), 1500); // same exit as a delivery: leaves the queue
    } catch (err) {
      if (err instanceof AuthRequiredError) { refresh(); return; }
      setError('Could not report this. Check your connection and try again.');
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="dcard dcard-done">
        <div className="dcard-done-msg">Delivered #{order.id}</div>
      </div>
    );
  }
  if (reported) {
    return (
      <div className="dcard dcard-done dcard-reported">
        <div className="dcard-done-msg">Reported #{order.id} — bring it back to the store</div>
      </div>
    );
  }

  return (
    <div className={`dcard ${confirming || failing ? 'dcard-active' : ''}`}>
      {/* Header */}
      <div className="dcard-head">
        <span className="dcard-id">#{order.id}</span>
        <span className="dcard-time">{fmtTime(order.placedAt)}</span>
        <span className={`dcard-pay ${order.paymentMethod === 'COD' ? 'cod' : 'upi'}`}>
          {order.paymentMethod === 'COD' ? `Collect ${fmtAmount(order.total)}` : `UPI Paid`}
        </span>
      </div>

      {/* Customer */}
      <div className="dcard-customer">
        <h2 className="dcard-name">{order.customerName}</h2>
        <a className="dcard-phone" href={`tel:${order.phone}`}>
          📞 {order.phone}
        </a>
      </div>

      {/* Address */}
      <div className="dcard-address">
        <span className="dcard-addr-text">{order.address.line}</span>
        <a className="dcard-maps" href={mapsUrl} target="_blank" rel="noreferrer">
          Open Maps ↗
        </a>
      </div>

      {/* Items toggle */}
      <button
        type="button"
        className="dcard-items-toggle"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-controls={`items-${order.id}`}
      >
        {order.items.length} item{order.items.length !== 1 ? 's' : ''} · {fmtAmount(order.total)}
        <span className="dcard-chevron" aria-hidden>{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <ul className="dcard-items" id={`items-${order.id}`}>
          {order.items.map((item, i) => (
            <li key={i} className="dcard-item">
              <span>{item.productName} <span className="dcard-item-label">{item.label}</span> × {item.qty}</span>
              <span>{fmtAmount(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
      )}

      {/* Actions / OTP area */}
      {failing ? (
        <div className="dcard-otp-area" role="group" aria-label="Why couldn't you deliver?">
          <p className="dcard-otp-label">Why couldn&apos;t you deliver?</p>
          {FAIL_REASONS.map((r) => (
            <label key={r} className="dcard-reason">
              <input
                type="radio"
                name={`fail-${order.id}`}
                value={r}
                checked={failReason === r}
                onChange={() => setFailReason(r)}
              />
              <span>{r}</span>
            </label>
          ))}
          <p className="dcard-fail-hint">
            Bring the order back to the store. Staff will call the customer and re-send or cancel it.
          </p>
          <div className="dcard-otp-row">
            <button
              type="button"
              className="btn btn-primary"
              disabled={!failReason || busy}
              onClick={submitFailure}
            >
              {busy ? '…' : 'Report'}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => { setFailing(false); setFailReason(''); setError(null); }}
              disabled={busy}
            >
              Back
            </button>
          </div>
          {error && <p className="field-error">{error}</p>}
        </div>
      ) : !confirming ? (
        <div className="dcard-actions">
          <button
            type="button"
            className="btn btn-primary dcard-deliver-btn"
            onClick={() => setConfirming(true)}
          >
            Confirm Delivery
          </button>
          <button
            type="button"
            className="btn btn-ghost dcard-fail-btn"
            onClick={() => setFailing(true)}
          >
            Can&apos;t deliver
          </button>
        </div>
      ) : (
        <div className="dcard-otp-area">
          <label className="dcard-otp-label" htmlFor={`otp-${order.id}`}>
            Enter OTP from customer
          </label>
          <div className="dcard-otp-row">
            <input
              id={`otp-${order.id}`}
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              autoComplete="one-time-code"
              className="dcard-otp-input"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="1234"
              autoFocus
              maxLength={6}
            />
            <button
              type="button"
              className="btn btn-primary"
              disabled={!otp.trim() || busy}
              onClick={submitOtp}
            >
              {busy ? '…' : 'Done'}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => { setConfirming(false); setOtp(''); setError(null); }}
              disabled={busy}
            >
              Back
            </button>
          </div>
          {error && <p className="field-error">{error}</p>}
        </div>
      )}
    </div>
  );
}
