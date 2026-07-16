'use client';

import { useState } from 'react';
import { ApiError, AuthRequiredError, confirmDelivery } from '@/app/lib/api';
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

export default function DeliveryCard({ order, onDelivered }: Props) {
  const { refresh } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

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

  if (done) {
    return (
      <div className="dcard dcard-done">
        <div className="dcard-done-msg">Delivered #{order.id}</div>
      </div>
    );
  }

  return (
    <div className={`dcard ${confirming ? 'dcard-active' : ''}`}>
      {/* Header */}
      <div className="dcard-head">
        <span className="dcard-id">#{order.id}</span>
        <span className="dcard-time">{fmtTime(order.placedAt)}</span>
        <span className={`dcard-pay ${order.paymentMethod === 'COD' ? 'cod' : 'upi'}`}>
          {order.paymentMethod === 'COD' ? `COD ${fmtAmount(order.total)}` : `UPI Paid`}
        </span>
      </div>

      {/* Customer */}
      <div className="dcard-customer">
        <div className="dcard-name">{order.customerName}</div>
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
      >
        {order.items.length} item{order.items.length !== 1 ? 's' : ''} · {fmtAmount(order.total)}
        <span className="dcard-chevron">{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <ul className="dcard-items">
          {order.items.map((item, i) => (
            <li key={i} className="dcard-item">
              <span>{item.productName} <span className="dcard-item-label">{item.label}</span> × {item.qty}</span>
              <span>{fmtAmount(item.lineTotal)}</span>
            </li>
          ))}
        </ul>
      )}

      {/* Actions / OTP area */}
      {!confirming ? (
        <button
          type="button"
          className="btn btn-primary dcard-deliver-btn"
          onClick={() => setConfirming(true)}
        >
          Confirm Delivery
        </button>
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
