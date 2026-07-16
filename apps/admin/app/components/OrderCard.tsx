'use client';

import { useState } from 'react';
import {
  ApiError,
  AuthRequiredError,
  assignOrder,
  transitionOrder,
} from '@/app/lib/api';
import { formatRupees, formatTime } from '@/app/lib/format';
import { STATUS_LABELS, canCancel, nextStatus } from '@/app/lib/status';
import type { DeliveryAgent, Order } from '@/app/lib/types';

function agentLabel(a: DeliveryAgent): string {
  return a.name || a.email || a.phone || `Agent #${a.id}`;
}

/**
 * One order in the admin queue: customer/contact/address/items/total, plus
 * one-tap status advance. The DELIVERED transition requires the customer's
 * delivery OTP (proof of delivery / COD safeguard, ARCHITECTURE §3.5), so the
 * advance button reveals an OTP prompt before sending. Cancel asks for a reason.
 * A rider dropdown dispatches the order to a delivery agent.
 */
export default function OrderCard({
  order,
  agents,
  onUpdated,
}: {
  order: Order;
  agents: DeliveryAgent[];
  onUpdated: (o: Order) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [otpPrompt, setOtpPrompt] = useState(false);
  const [otp, setOtp] = useState('');

  const next = nextStatus(order.status);
  const terminal = order.status === 'DELIVERED' || order.status === 'CANCELLED';
  const assignedAgent =
    agents.find((a) => a.id === order.assignedAgentId) ?? null;

  async function onAssign(value: string) {
    const agentId = value === '' ? null : Number(value);
    setBusy(true);
    setError(null);
    try {
      const updated = await assignOrder(order.id, agentId);
      onUpdated(updated);
    } catch (err) {
      if (err instanceof AuthRequiredError) {
        setError('Session expired — please log in again.');
      } else {
        setError('Could not update the assignment. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function advance(otpValue?: string) {
    if (!next) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await transitionOrder(order.id, {
        to: next,
        deliveryOtp: otpValue,
      });
      onUpdated(updated);
      setOtpPrompt(false);
      setOtp('');
    } catch (err) {
      if (err instanceof AuthRequiredError) {
        setError('Session expired — please log in again.');
      } else if (
        err instanceof ApiError &&
        (err.status === 400 || err.status === 422)
      ) {
        setError(
          next === 'DELIVERED'
            ? 'Incorrect delivery code. Please re-check with the customer.'
            : 'That transition was rejected. Refresh and try again.',
        );
      } else {
        setError('Could not update the order. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  function onAdvanceClick() {
    if (next === 'DELIVERED') {
      setOtpPrompt(true);
      return;
    }
    void advance();
  }

  async function cancel() {
    const reason = window.prompt('Reason for cancelling this order?');
    if (reason == null) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await transitionOrder(order.id, {
        to: 'CANCELLED',
        reason: reason || 'Cancelled by staff',
      });
      onUpdated(updated);
    } catch (err) {
      if (err instanceof AuthRequiredError) {
        setError('Session expired — please log in again.');
      } else {
        setError('Could not cancel the order. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className={`order-card status-${order.status}`}>
      <header className="order-card-head">
        <div>
          <span className="order-id">#{order.id}</span>
          <span className="order-time">{formatTime(order.placedAt)}</span>
        </div>
        <span className={`status-badge status-${order.status}`}>
          {STATUS_LABELS[order.status]}
        </span>
      </header>

      <div className="order-customer">
        <strong>{order.customerName}</strong>
        <a href={`tel:${order.phone}`}>{order.phone}</a>
        <span className="muted">{order.address.line}</span>
      </div>

      <div className="order-assign">
        <label htmlFor={`assign-${order.id}`}>Rider</label>
        {terminal ? (
          <span className="muted">
            {assignedAgent ? agentLabel(assignedAgent) : 'Unassigned'}
          </span>
        ) : (
          <select
            id={`assign-${order.id}`}
            value={order.assignedAgentId ?? ''}
            disabled={busy || agents.length === 0}
            onChange={(e) => onAssign(e.target.value)}
          >
            <option value="">
              {agents.length === 0 ? 'No agents available' : 'Unassigned'}
            </option>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {agentLabel(a)}
              </option>
            ))}
          </select>
        )}
      </div>

      <ul className="order-card-items">
        {order.items.map((item, idx) => (
          <li key={idx}>
            <span>
              {item.productName}{' '}
              <span className="muted">
                ({item.label}) × {item.qty}
              </span>
            </span>
            <span>{formatRupees(item.lineTotal)}</span>
          </li>
        ))}
      </ul>

      <div className="order-card-foot">
        <span className="order-pay">
          {order.paymentMethod === 'COD' ? 'COD' : 'UPI'} · {order.paymentStatus}
        </span>
        <strong className="order-total">{formatRupees(order.total)}</strong>
      </div>

      {error && <p className="order-error">{error}</p>}

      {otpPrompt ? (
        <div className="otp-prompt">
          <label htmlFor={`otp-${order.id}`}>
            Enter the customer&apos;s delivery code
          </label>
          <div className="otp-prompt-row">
            <input
              id={`otp-${order.id}`}
              inputMode="numeric"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="Delivery OTP"
              autoFocus
            />
            <button
              type="button"
              className="btn"
              disabled={busy || otp.trim().length === 0}
              onClick={() => advance(otp.trim())}
            >
              {busy ? '…' : 'Confirm delivery'}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={busy}
              onClick={() => {
                setOtpPrompt(false);
                setOtp('');
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="order-actions">
          {next ? (
            <button
              type="button"
              className="btn"
              disabled={busy}
              onClick={onAdvanceClick}
            >
              {busy ? 'Updating…' : `Mark ${STATUS_LABELS[next]}`}
            </button>
          ) : (
            <span className="muted">
              {order.status === 'DELIVERED'
                ? 'Completed'
                : 'No further action'}
            </span>
          )}
          {canCancel(order.status) && (
            <button
              type="button"
              className="btn btn-ghost danger"
              disabled={busy}
              onClick={cancel}
            >
              Cancel
            </button>
          )}
        </div>
      )}
    </article>
  );
}
