'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  ApiError,
  addCartItem,
  createCart,
  getCart,
  removeCartItem,
  updateCartItem,
} from '@/app/lib/api';
import { loadAuth } from '@/app/lib/auth';
import { clearCartId, loadCartId, saveCartId } from '@/app/lib/cart';
import {
  acceptCartPrices,
  acceptVariantPrice,
  cartPriceChanges,
  clearCartPrices,
  type PriceChange,
} from '@/app/lib/cartPrices';
import type { Cart } from '@/app/lib/types';

// Cart state shared across the storefront shell: the header badge, the
// AddToCartButton, and the cart page all read/mutate through this context.
// The server is the source of truth; every mutation returns the fresh cart.
//
// Split deliberately into TWO contexts. The actions never change identity —
// they read the current cart from a ref rather than closing over it — so a
// component that only needs `refresh` or `reset` subscribes to a value that is
// created once and never re-renders it. Only the components that actually
// display cart data subscribe to the state.

/** Why a quantity change was rejected, for the control to phrase as it likes. */
export type VariantError = 'stock' | 'failed';

interface CartStateValue {
  cart: Cart | null;
  itemCount: number;
  loading: boolean;
  /**
   * Quantity of a given variant as the customer should see it right now: the
   * pending target while taps are still being coalesced, otherwise the
   * server-confirmed line quantity.
   */
  qtyOf: (variantId: string) => number;
  /** True while this variant has a coalesced change not yet acknowledged. */
  isSyncing: (variantId: string) => boolean;
  /** Why this variant's last quantity change failed, if it did. */
  errorOf: (variantId: string) => VariantError | undefined;
  /**
   * Lines whose unit price changed since the customer accepted it — a store
   * admin edited the selling price while the item sat in the cart. Non-empty
   * blocks checkout until {@link CartActionsValue.acknowledgePriceChanges} is
   * called, so a reprice can never be adopted silently on the customer's behalf.
   */
  priceChanges: PriceChange[];
}

interface CartActionsValue {
  /**
   * Move a variant's quantity by {@code delta}, the control for both steppers.
   *
   * <p>Returns immediately and never rejects: the new quantity is shown at once
   * and the server call is coalesced. Tapping "+" five times quickly sends ONE
   * request for +5 rather than five requests — and, more to the point, no longer
   * loses four of the taps. The steppers used to disable themselves for the
   * duration of each round-trip, so on a phone on mobile data every tap after
   * the first landed on a disabled button and was silently dropped.
   */
  nudgeVariant: (variantId: string, delta: number) => void;
  /** Add a variant at an explicit quantity (0 is a no-op). */
  addItem: (variantId: string, qty: number) => Promise<Cart>;
  /** Set a line's quantity by line id (0 removes) — the cart page's editor. */
  setQty: (itemId: string, qty: number) => Promise<Cart>;
  /** Remove a line entirely. */
  removeItem: (itemId: string) => Promise<Cart>;
  /** Re-fetch the cart from the server (e.g. on cart-page mount). */
  refresh: () => Promise<void>;
  /** Forget the local cart (called after a successful order). */
  reset: () => void;
  /** The customer has seen the new prices: accept them and unblock checkout. */
  acknowledgePriceChanges: () => void;
}

const CartStateContext = createContext<CartStateValue | null>(null);
const CartActionsContext = createContext<CartActionsValue | null>(null);

/**
 * Cart actions only. Prefer this wherever the component does not display cart
 * data: the value is created once, so cart updates never re-render the caller.
 */
export function useCartActions(): CartActionsValue {
  const ctx = useContext(CartActionsContext);
  if (!ctx) throw new Error('useCartActions must be used within <CartProvider>');
  return ctx;
}

/** Cart state and actions together, for components that render cart data. */
export function useCart(): CartStateValue & CartActionsValue {
  const state = useContext(CartStateContext);
  const actions = useContext(CartActionsContext);
  if (!state || !actions) throw new Error('useCart must be used within <CartProvider>');
  return useMemo(() => ({ ...state, ...actions }), [state, actions]);
}

/**
 * How long to wait for more taps before sending a variant's new quantity. Long
 * enough to fold a burst of taps into one request, short enough that a single
 * tap is confirmed by the time the customer looks away. The displayed number
 * does not wait for this — it changes on the tap.
 */
const COALESCE_MS = 250;

// Recompute a cart locally after setting one variant's quantity, so the UI can
// update instantly (optimistically) before the server round-trip returns. A
// qty <= 0 drops the line. Line/sub totals are derived from the known unitPrice.
function withVariantQty(cart: Cart, variantId: string, qty: number): Cart {
  const items = cart.items
    .map((i) =>
      i.variantId === variantId
        ? { ...i, qty, lineTotal: Math.round(i.unitPrice * qty * 100) / 100 }
        : i,
    )
    .filter((i) => i.qty > 0);
  const subtotal = items.reduce((sum, i) => sum + i.lineTotal, 0);
  const itemCount = items.reduce((sum, i) => sum + i.qty, 0);
  return { ...cart, items, subtotal: Math.round(subtotal * 100) / 100, itemCount };
}

function lineQty(cart: Cart | null, variantId: string): number {
  return cart?.items.find((i) => i.variantId === variantId)?.qty ?? 0;
}

export default function CartProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(false);
  // Guards lazy cart creation against concurrent first-adds.
  const creating = useRef<Promise<string> | null>(null);

  // The cart, readable synchronously from a callback. This is what lets every
  // action below keep a stable identity: none of them closes over `cart`.
  const cartRef = useRef<Cart | null>(null);
  const commit = useCallback((next: Cart | null) => {
    cartRef.current = next;
    setCart(next);
  }, []);

  // Coalescing state for the steppers. The refs are the authority (callbacks
  // read them synchronously); the state copies exist only so rendering updates.
  // Both are written together, so they cannot drift.
  const targetsRef = useRef(new Map<string, number>());
  const errorsRef = useRef(new Map<string, VariantError>());
  const [targets, setTargets] = useState<ReadonlyMap<string, number>>(new Map());
  const [errors, setErrors] = useState<ReadonlyMap<string, VariantError>>(new Map());
  const publishTargets = useCallback(() => setTargets(new Map(targetsRef.current)), []);
  const publishErrors = useCallback(() => setErrors(new Map(errorsRef.current)), []);

  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const flushing = useRef(new Set<string>());

  // Hydrate from a persisted cartId on mount.
  useEffect(() => {
    const id = loadCartId();
    if (!id) return;
    setLoading(true);
    getCart(id)
      .then((fetched) => {
        cartRef.current = fetched;
        setCart(fetched);
      })
      .catch(() => {
        // Stale/expired cartId — forget it and start fresh on next add.
        clearCartId();
        cartRef.current = null;
        setCart(null);
      })
      .finally(() => setLoading(false));
  }, []);

  // Forget the cart when the session ends (logout, or a failed token refresh
  // clearing it). Now that carts follow the ACCOUNT — owned at creation and
  // recoverable via /carts/mine on the next login — keeping the id in this
  // browser would only hand the previous user's basket to whoever uses a
  // shared device next. Nothing is lost: the server still holds the cart.
  useEffect(() => {
    const onAuthChanged = () => {
      if (!loadAuth()) {
        clearCartId();
        cartRef.current = null;
        setCart(null);
      }
    };
    window.addEventListener('tb:auth-changed', onAuthChanged);
    return () => window.removeEventListener('tb:auth-changed', onAuthChanged);
  }, []);

  // Don't leave a coalesced change unsent when the provider unmounts.
  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach((timer) => clearTimeout(timer));
      pending.clear();
    };
  }, []);

  const ensureCartId = useCallback(async (): Promise<string> => {
    const existing = loadCartId();
    if (existing) return existing;
    if (creating.current) return creating.current;
    creating.current = createCart()
      .then(({ cartId }) => {
        saveCartId(cartId);
        return cartId;
      })
      .finally(() => {
        creating.current = null;
      });
    return creating.current;
  }, []);

  const refresh = useCallback(async (): Promise<void> => {
    const id = loadCartId();
    if (!id) {
      commit(null);
      return;
    }
    setLoading(true);
    try {
      commit(await getCart(id));
    } catch {
      clearCartId();
      commit(null);
    } finally {
      setLoading(false);
    }
  }, [commit]);

  /**
   * Send one variant's coalesced quantity. Reads the target at the moment of
   * sending, so every tap up to then is included in a single request.
   */
  const flush = useCallback(
    async (variantId: string): Promise<void> => {
      timers.current.delete(variantId);
      // A request for this variant is already in flight; the `finally` below
      // picks up anything queued since, so ordering stays per-variant serial.
      if (flushing.current.has(variantId)) return;
      const target = targetsRef.current.get(variantId);
      if (target === undefined) return;

      flushing.current.add(variantId);
      try {
        const id = await ensureCartId();
        const line = cartRef.current?.items.find((i) => i.variantId === variantId);
        const updated = line
          ? await updateCartItem(id, line.itemId, target)
          : await addCartItem(id, variantId, target);

        // Clear the pending target only if it is still the number we just sent.
        // A tap during the request left a newer one, which must survive so the
        // display does not jump backwards to the quantity the server confirmed.
        if (targetsRef.current.get(variantId) === target) {
          targetsRef.current.delete(variantId);
          publishTargets();
        }
        commit(updated);
        // The price the customer was looking at when they chose the quantity is
        // the baseline a later admin edit is measured against.
        acceptVariantPrice(updated, variantId);
      } catch (err) {
        // The server refused (no stock left, line gone). Its cart is the truth:
        // drop the optimistic target, resync, and tell the control why.
        targetsRef.current.delete(variantId);
        publishTargets();
        const outOfStock = err instanceof ApiError && err.status === 409;
        errorsRef.current.set(variantId, outOfStock ? 'stock' : 'failed');
        publishErrors();
        await refresh();
      } finally {
        flushing.current.delete(variantId);
        if (targetsRef.current.has(variantId)) {
          void flush(variantId);
        }
      }
    },
    [commit, ensureCartId, publishErrors, publishTargets, refresh],
  );

  const nudgeVariant = useCallback(
    (variantId: string, delta: number): void => {
      // Count from the pending target, not from the cart: three quick taps must
      // reach 3, and the first two are not in the server's cart yet.
      const base = targetsRef.current.get(variantId) ?? lineQty(cartRef.current, variantId);
      const next = Math.max(0, base + delta);
      if (next === base) return;

      targetsRef.current.set(variantId, next);
      publishTargets();
      if (errorsRef.current.delete(variantId)) {
        publishErrors();
      }

      // Optimistic totals, but only for a line already in the cart: a brand-new
      // line has no known unit price, so its money waits for the server even
      // though its quantity does not.
      const current = cartRef.current;
      if (current?.items.some((i) => i.variantId === variantId)) {
        commit(withVariantQty(current, variantId, next));
      }

      const existing = timers.current.get(variantId);
      if (existing) clearTimeout(existing);
      timers.current.set(
        variantId,
        setTimeout(() => void flush(variantId), COALESCE_MS),
      );
    },
    [commit, flush, publishErrors, publishTargets],
  );

  const addItem = useCallback(
    async (variantId: string, qty: number): Promise<Cart> => {
      const id = await ensureCartId();
      const prev = cartRef.current;
      const existing = prev?.items.find((i) => i.variantId === variantId);
      if (prev && existing) {
        commit(withVariantQty(prev, variantId, existing.qty + qty));
      }
      try {
        const updated = await addCartItem(id, variantId, qty);
        commit(updated);
        acceptVariantPrice(updated, variantId);
        return updated;
      } catch (err) {
        if (prev) commit(prev); // roll back the optimistic change
        throw err;
      }
    },
    [commit, ensureCartId],
  );

  const setQty = useCallback(
    async (itemId: string, qty: number): Promise<Cart> => {
      const id = loadCartId();
      if (!id) throw new Error('No cart');
      const prev = cartRef.current;
      const line = prev?.items.find((i) => i.itemId === itemId);
      if (prev && line) commit(withVariantQty(prev, line.variantId, qty));
      try {
        const updated = await updateCartItem(id, itemId, qty);
        commit(updated);
        return updated;
      } catch (err) {
        if (prev) commit(prev);
        throw err;
      }
    },
    [commit],
  );

  const removeItem = useCallback(
    async (itemId: string): Promise<Cart> => {
      const id = loadCartId();
      if (!id) throw new Error('No cart');
      const updated = await removeCartItem(id, itemId);
      commit(updated);
      return updated;
    },
    [commit],
  );

  const reset = useCallback(() => {
    clearCartId();
    clearCartPrices();
    targetsRef.current.clear();
    errorsRef.current.clear();
    publishTargets();
    publishErrors();
    commit(null);
  }, [commit, publishErrors, publishTargets]);

  const acknowledgePriceChanges = useCallback(() => {
    const current = cartRef.current;
    if (current) {
      acceptCartPrices(current);
      // Re-run the diff against the new pin by nudging the cart reference; the
      // object is unchanged, only the baseline it is compared to.
      commit({ ...current });
    }
  }, [commit]);

  // Created once. Every action reads the live cart from a ref, so none of them
  // depends on it and this object never changes identity — a consumer of
  // actions alone never re-renders because the cart changed.
  const actions = useMemo<CartActionsValue>(
    () => ({
      nudgeVariant,
      addItem,
      setQty,
      removeItem,
      refresh,
      reset,
      acknowledgePriceChanges,
    }),
    [nudgeVariant, addItem, setQty, removeItem, refresh, reset, acknowledgePriceChanges],
  );

  // Derived, not stored: every render compares the live cart against the pinned
  // prices, so a refresh that repriced a line shows up immediately instead of
  // being folded into the displayed total.
  const priceChanges = useMemo(() => cartPriceChanges(cart), [cart]);

  const state = useMemo<CartStateValue>(() => {
    const displayedQty = (variantId: string) =>
      targets.get(variantId) ?? lineQty(cart, variantId);
    // The badge counts what the customer just tapped, including a quantity
    // still on its way to the server — over the union of the cart's lines and
    // the pending targets, since a first add is not a line yet.
    const counted = new Set<string>([
      ...(cart?.items ?? []).map((i) => i.variantId),
      ...targets.keys(),
    ]);
    return {
      cart,
      itemCount:
        targets.size === 0
          ? cart?.itemCount ?? 0
          : [...counted].reduce((sum, variantId) => sum + displayedQty(variantId), 0),
      loading,
      qtyOf: displayedQty,
      isSyncing: (variantId: string) => targets.has(variantId),
      errorOf: (variantId: string) => errors.get(variantId),
      priceChanges,
    };
  }, [cart, loading, targets, errors, priceChanges]);

  return (
    <CartActionsContext.Provider value={actions}>
      <CartStateContext.Provider value={state}>{children}</CartStateContext.Provider>
    </CartActionsContext.Provider>
  );
}
