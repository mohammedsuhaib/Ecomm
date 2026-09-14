// The unit prices the customer has actually been shown and accepted.
//
// Cart lines are priced LIVE from the catalogue on every read, so a selling
// price a store admin edits while an item sits in someone's cart changes that
// cart's total the next time it is fetched. Without a record of what the
// customer had agreed to, the checkout page simply re-read the cart, displayed
// the new total and sent it back as `expectedTotal` — so the server's
// "the total has changed" guard compared the new total against itself and
// always passed. The customer was never told the price moved.
//
// So we pin the prices at the moment the customer accepts them (adding an item,
// or acknowledging a change) and diff the live cart against that pin. The pin is
// per-variant, not a single total, so a change can be named item by item and two
// offsetting changes can't cancel out into a total that looks unchanged.
//
// Prices are held as integer paise: cart prices arrive as JSON numbers, and
// comparing those for equality in floating point is a bug waiting to happen.

import type { Cart } from './types';

const PINNED_PRICES_KEY = 'tb.cartPrices.v1';

interface PinnedPrices {
  /** The cart these prices belong to; a different cart means no baseline. */
  cartId: string;
  /** variantId -> accepted unit price, in paise. */
  units: Record<string, number>;
}

/** A line whose unit price moved since the customer last accepted it. */
export interface PriceChange {
  variantId: string;
  productName: string;
  productNameKn?: string | null;
  label: string;
  /** Accepted and current unit price, in decimal rupees. */
  was: number;
  now: number;
}

function toPaise(rupees: number): number {
  return Math.round(rupees * 100);
}

function read(): PinnedPrices | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(PINNED_PRICES_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PinnedPrices;
    if (!parsed || typeof parsed.cartId !== 'string' || !parsed.units) return null;
    return parsed;
  } catch {
    return null; // unavailable or corrupt -> no baseline, so nothing is flagged
  }
}

function write(value: PinnedPrices): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(PINNED_PRICES_KEY, JSON.stringify(value));
  } catch {
    /* private mode / full storage: we lose the baseline, never the cart */
  }
}

/**
 * Record every current line price as accepted. Call this when the customer has
 * seen and gone along with these prices — after an add, or when they
 * acknowledge a change — never on a plain background re-read, which is exactly
 * the silent adoption this module exists to prevent.
 */
export function acceptCartPrices(cart: Cart): void {
  const units: Record<string, number> = {};
  for (const item of cart.items) {
    units[item.variantId] = toPaise(item.unitPrice);
  }
  write({ cartId: cart.cartId, units });
}

/**
 * Record one variant's price as accepted, keeping the rest of the pin. Used on
 * add-to-cart: the price on the button is the price the customer chose to add
 * at, and the other lines' baselines must not be disturbed.
 */
export function acceptVariantPrice(cart: Cart, variantId: string): void {
  const line = cart.items.find((i) => i.variantId === variantId);
  if (!line) return;
  const existing = read();
  const units =
    existing && existing.cartId === cart.cartId ? { ...existing.units } : {};
  units[variantId] = toPaise(line.unitPrice);
  write({ cartId: cart.cartId, units });
}

export function clearCartPrices(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(PINNED_PRICES_KEY);
  } catch {
    /* ignore */
  }
}

/**
 * Lines whose unit price differs from what the customer accepted.
 *
 * Empty when there is no baseline for this cart — a fresh device, cleared
 * storage, or a cart id that changed when a guest cart merged into an account
 * on login. That is deliberate: with nothing to compare against we must not
 * invent a price change and block checkout on it. The server's own
 * `expectedTotal` check still covers a change landing mid-submit.
 *
 * A line added since the pin was written has no baseline either, so it is not
 * reported; the customer just chose its price.
 */
export function cartPriceChanges(cart: Cart | null): PriceChange[] {
  if (!cart) return [];
  const pinned = read();
  if (!pinned || pinned.cartId !== cart.cartId) return [];

  const changes: PriceChange[] = [];
  for (const item of cart.items) {
    const accepted = pinned.units[item.variantId];
    if (accepted === undefined) continue;
    if (accepted !== toPaise(item.unitPrice)) {
      changes.push({
        variantId: item.variantId,
        productName: item.productName,
        productNameKn: item.productNameKn,
        label: item.label,
        was: accepted / 100,
        now: item.unitPrice,
      });
    }
  }
  return changes;
}
