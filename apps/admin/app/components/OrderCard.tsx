'use client';

import { useState } from 'react';
import {
  ApiError,
  AuthRequiredError,
  assignOrder,
  serverMessage,
  transitionOrder,
} from '@/app/lib/api';
import { formatRupees, formatTime } from '@/app/lib/format';
import { STATUS_LABELS, awaitsRiderPickup, canCancel, nextStatus } from '@/app/lib/status';
import type { DeliveryAgent, Order } from '@/app/lib/types';

function agentLabel(a: DeliveryAgent): string {
  return a.name || a.email || `Agent #${a.id}`;
}

/**
 * Why a delivery cannot be recorded without a rider, phrased as the next
 * action. The delivery and any cash collected are booked against the rider, so
 * an unassigned delivery is money the store thinks it has with nobody holding
 * it — which is why the API refuses rather than accepting it quietly.
 */
const NO_RIDER_FOR_DELIVERY =
  'Assign a rider before marking this delivered — the delivery and any cash '
  + 'collected are recorded against them. Pick one from Rider above.';

/**
 * One order in the admin queue: customer/contact/address/items/total, plus
 * one-tap status advance. A new order arrives PLACED and the first advance is
 * the staff confirmation (ARCHITECTURE §3.5) — nothing confirms it before a
 * person has looked at it. The DELIVERED transition requires the customer's
 * delivery OTP (proof of delivery / COD safeguard), so the advance button
 * reveals an OTP prompt before sending. Cancel asks for a reason. A rider
 * dropdown dispatches the order to a delivery agent.
 */
export default function OrderCard({
  order,
  agents,
  onUpdated,
  onAgentsStale,
}: {
  order: Order;
  agents: DeliveryAgent[];
  onUpdated: (o: Order) => void;
  /** Ask the queue to re-read the roster — the one we were given is out of date. */
  onAgentsStale?: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [otpPrompt, setOtpPrompt] = useState(false);
  const [otp, setOtp] = useState('');

  const next = nextStatus(order.status);
  const terminal = order.status === 'DELIVERED' || order.status === 'CANCELLED';
  // The goods have left the shop. Assigning a rider here is recording who is
  // holding them, not handing out a new job — so an off-duty rider stays
  // selectable, which the API allows for exactly this state. Without it an
  // order whose rider went off duty before anyone marked it delivered could
  // not be assigned, and DELIVERED requires an assignment: the only remaining
  // transition would be cancelling a delivery that actually happened.
  const goodsAreOut =
    order.status === 'OUT_FOR_DELIVERY' || order.status === 'DELIVERY_FAILED';
  // Why the bag came back — the rider's reason from the latest failed attempt.
  // It is the one input staff need to choose between re-dispatch and cancel.
  const failureReason =
    order.status === 'DELIVERY_FAILED'
      ? [...order.timeline].reverse().find((e) => e.toStatus === 'DELIVERY_FAILED')?.note ?? null
      : null;
  const assignedAgent =
    agents.find((a) => a.id === order.assignedAgentId) ?? null;
  // The roster is active riders only, so an order still held by a rider who has
  // been deactivated finds no match here — and a <select> whose value matches
  // no <option> displays the FIRST one instead, i.e. "Unassigned" on an order
  // that is very much assigned. Staff reading that would dispatch a second
  // rider to a bag the first one is already carrying. Carry the missing rider
  // as an explicit option so the card keeps saying who holds it.
  const missingAssigneeId =
    order.assignedAgentId != null && assignedAgent == null
      ? order.assignedAgentId
      : null;

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
        // The server says WHY the rider can't take it — deactivated, or gone
        // off duty since the list loaded — and "try again" would be wrong
        // advice for either. Show its reason when it gave one, and re-read the
        // roster so the name that was just refused stops being offered.
        const reason = serverMessage(err);
        if (reason) onAgentsStale?.();
        setError(reason ?? 'Could not update the assignment. Please try again.');
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
        // Not serverMessage() here: the transition guard speaks in state-machine
        // terms ("Illegal transition PACKING -> CONFIRMED"), and the way staff
        // reach it is a card that went stale behind them — which "refresh and
        // try again" answers and the server's own wording does not.
        //
        // DELIVERED now has TWO ways to be refused, and the card can tell them
        // apart from what it already knows rather than by reading the server's
        // sentence: with no rider assigned the server refuses before it ever
        // compares the code, so blaming the delivery code would send staff back
        // to the customer to re-read a number that was right all along.
        setError(
          next === 'DELIVERED'
            ? order.assignedAgentId == null
              ? NO_RIDER_FOR_DELIVERY
              : 'Incorrect delivery code. Please re-check with the customer.'
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
      // Say it before asking for the code, not after: the server refuses an
      // unassigned order regardless of the code, so prompting first would have
      // staff read six digits off a customer's phone for nothing.
      if (order.assignedAgentId == null) {
        setError(NO_RIDER_FOR_DELIVERY);
        return;
      }
      setOtpPrompt(true);
      return;
    }
    void advance();
  }

  async function cancel() {
    // The audience is stated because it changed: this reason is now shown to
    // the customer on their order screen, word for word. Staff writing an
    // internal note here would be writing to the customer without knowing it.
    const reason = window.prompt(
      'Why are you cancelling this order?\n\n' +
        'The customer will see this on their order page, exactly as you type it.',
    );
    if (reason == null) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await transitionOrder(order.id, {
        // Blank means blank. It used to become "Cancelled by staff", which is
        // now a sentence the customer reads under "Reason:" while saying
        // nothing their notice does not already say — and saying it in English
        // whichever language they shop in. With no reason the storefront shows
        // the plain cancellation notice.
        to: 'CANCELLED',
        reason: reason.trim() || undefined,
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
          <span className="order-id">#{order.publicCode}</span>
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

      {failureReason && (
        <p className="order-fail-reason" role="status">
          <strong>Couldn&apos;t deliver:</strong> {failureReason}
          <span className="muted"> — stock is still reserved; re-dispatch, or cancel once the goods are back.</span>
        </p>
      )}

      {/* Packed and bagged, waiting on the counter. Say whose move it is: the
          rider's app marks it collected, which is what starts the customer's
          live map and delivery code. Staff still have the button for the day a
          rider's phone is flat, so the note explains what it records rather
          than hiding it. */}
      {awaitsRiderPickup(order.status) && (
        <p className="order-awaiting-pickup" role="status">
          <strong>Ready for the rider to collect.</strong>
          <span className="muted">
            {' '}
            {order.assignedAgentId == null
              ? 'Assign a rider above — they pick it up from their own app.'
              : 'They mark it picked up in the delivery app; use the button below only if you are sending it out for them.'}
          </span>
        </p>
      )}

      <div className="order-assign">
        <label htmlFor={`assign-${order.id}`}>Rider</label>
        {terminal ? (
          <span className="muted">
            {assignedAgent
              ? agentLabel(assignedAgent)
              : missingAssigneeId != null
                ? `Agent #${missingAssigneeId} (no longer active)`
                : 'Unassigned'}
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
            {missingAssigneeId != null && (
              <option value={missingAssigneeId}>
                Agent #{missingAssigneeId} (no longer active)
              </option>
            )}
            {agents.map((a) => {
              // Off duty = the rider's own switch. Keep them visible (so staff
              // see who exists) but not selectable while a new job could land
              // on someone who has gone home — the server refuses that too.
              // Once the goods are out it is a record, not a job, so it is
              // allowed on both sides (see goodsAreOut).
              const offDuty =
                a.onDuty === false
                && a.id !== order.assignedAgentId
                && !goodsAreOut;
              return (
                <option key={a.id} value={a.id} disabled={offDuty}>
                  {agentLabel(a)}{offDuty ? ' (off duty)' : ''}
                </option>
              );
            })}
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
          {order.paymentMethod === 'COD' ? 'Pay on delivery' : 'UPI'} · {order.paymentStatus}
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
              {busy
                ? 'Updating…'
                : order.status === 'DELIVERY_FAILED'
                  ? 'Re-dispatch'
                  : order.status === 'PLACED'
                    ? 'Confirm order'
                    : awaitsRiderPickup(order.status)
                      // Not "Mark Out for delivery": from here the normal move
                      // is the rider's own, so staff pressing this are
                      // recording a hand-over they did themselves.
                      ? 'Handed to rider'
                      : `Mark ${STATUS_LABELS[next]}`}
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
