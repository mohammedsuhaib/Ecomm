// A short-lived note that the customer has just placed an order.
//
// Why this exists: placing an order clears the local cart id (the order page
// calls CartProvider's reset(), so the next shop starts a fresh basket). Press
// the browser Back button from the confirmation screen and the cart and
// checkout pages find no cart at all, so they say "Your cart is empty" — which
// reads as if the order never happened, at the single most anxious moment in
// the flow. QA reported exactly that: expected 'Rejected "cart has already been
// ordered"', got "Your cart is empty".
//
// The server already refuses to order the same cart twice, so nothing here is a
// safety control — it is only how the customer is told what happened, and where
// their order went.

import type { Order } from './types';

const KEY = 'tb.lastOrder.v1';

// How long the note is worth showing. Long enough to cover the Back-button
// press and a confused re-navigation a few minutes later; short enough that
// next week's visit is not greeted with a stale order. The cart page's
// server-backed state is authoritative the moment a new cart exists.
const TTL_MS = 1000 * 60 * 30;

export interface LastOrder {
  /** The speakable code shown to the customer (e.g. C944E6N4). */
  code: string;
  /** Unguessable tracking token — the only id that can fetch the order. */
  token: string;
  /** Epoch ms, for the TTL above. */
  at: number;
}

export function rememberLastOrder(order: Order): void {
  if (typeof window === 'undefined') return;
  try {
    const payload: LastOrder = {
      code: order.publicCode,
      token: order.trackingToken,
      at: Date.now(),
    };
    window.localStorage.setItem(KEY, JSON.stringify(payload));
  } catch {
    /* storage unavailable (private mode) — the empty state just stays generic */
  }
}

export function loadLastOrder(): LastOrder | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as LastOrder;
    if (
      !parsed ||
      typeof parsed.code !== 'string' ||
      typeof parsed.token !== 'string' ||
      typeof parsed.at !== 'number' ||
      Date.now() - parsed.at > TTL_MS
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

/** Forget the note once the customer has started a new basket. */
export function clearLastOrder(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    /* nothing to do */
  }
}
