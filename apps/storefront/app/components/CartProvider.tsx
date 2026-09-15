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
import { clearLastOrder } from '@/app/lib/lastOrder';
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
    // A basket with something in it is a NEW basket, so the "you just ordered"
    // note has done its job — drop it. Without this the note outlives its
    // purpose: order, shop again, empty the basket, and the empty state would
    // announce the old order as if this basket were the one already placed.
    // Ordering itself passes null here (the order page resets the cart), so the
    // note survives exactly the Back-button case it exists for.
    if (next && next.items.length > 0) clearLastOrder();
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

  /**
   * Cart writes run one at a time, across all variants — not one per variant.
   * Every write returns the WHOLE cart, so two in flight together can finish
   * out of order and the older response then overwrites the newer one: tap
   * variant A (a POST that creates a line) and variant B (a cheaper PUT)
   * within the same breath, and A's reply — computed before B's write
   * committed — lands last and drops B's line from the display. The server has
   * both, and nothing re-reads the cart, so the wrong number sits there until
   * the customer opens the cart page. Serialising makes the last response
   * always the newest.
   */
  const writes = useRef<Promise<unknown>>(Promise.resolve());
  const enqueue = useCallback((task: () => Promise<void>): Promise<void> => {
    // Chained through `finally` so one failed write cannot stall the queue.
    const next = writes.current.then(task, task);
    writes.current = next.catch(() => undefined);
    return next;
  }, []);

  /**
   * Ordering for everything the server sends back.
   *
   * <p>Writes are serialised below (see `writes`) so they cannot land out of
   * order. Reads were not, and a read is just as capable of putting an old
   * cart back: the cart page refreshes on mount, so tapping Add and opening
   * the cart in the same breath has a GET and a POST in the air together. Let
   * the GET reach the server first and it is answered without the item; if its
   * reply then lands after the add's, it overwrites the cart with the version
   * that has no item in it. The customer is told the cart is empty — or, just
   * after ordering, is shown the order note again — while the server has
   * exactly what they added, so Add looks like it does nothing.
   *
   * <p>Which request was SENT first says nothing about this: the tap goes out
   * before the cart page's refresh, yet the debounce and a slower add can
   * still have the server answer the read first. What matters is whether a
   * write overlapped the read at all — if one did, its own reply describes the
   * cart afterwards, and the read is describing a moment that has passed. So a
   * read is committed only when no write was in flight when it started, none
   * started while it was out, and none is in flight when it comes back.
   *
   * <p>`stamp`/`commitFetched` keep the writes themselves in order; the ones
   * the queue does not cover (the cart page's own +/−/Remove) cannot then
   * cross each other either.
   */
  const issuedSeq = useRef(0);
  const appliedSeq = useRef(0);
  /** Stamp a request as it goes out. Call immediately before sending. */
  const stamp = useCallback(() => ++issuedSeq.current, []);
  const commitFetched = useCallback(
    (seq: number, next: Cart | null) => {
      if (seq < appliedSeq.current) return;
      appliedSeq.current = seq;
      commit(next);
    },
    [commit],
  );

  // Writes in flight, and a counter that moves whenever one starts or ends —
  // together they tell a read whether anything changed under it.
  const writesInFlight = useRef(0);
  const writeEpoch = useRef(0);
  const beginWrite = useCallback(() => {
    writesInFlight.current += 1;
    writeEpoch.current += 1;
  }, []);
  const endWrite = useCallback(() => {
    writesInFlight.current -= 1;
    writeEpoch.current += 1;
  }, []);

  /**
   * Read the cart, and say so when a write overlapped the read instead of
   * returning a cart that is already out of date. Nothing is committed in that
   * case: the overlapping write's own reply is the newer picture.
   */
  const readCart = useCallback(async (id: string): Promise<Cart | null> => {
    const epoch = writeEpoch.current;
    const busy = writesInFlight.current;
    const cart = await getCart(id);
    if (busy > 0 || writesInFlight.current > 0 || writeEpoch.current !== epoch) {
      return null;
    }
    return cart;
  }, []);

  /**
   * Take a cart as the server just gave it to us.
   *
   * <p>A cart that has become an order is finished — the server keeps it for
   * the order's sake but refuses every write — so keeping its id would show
   * the customer the items they already bought as if they were still shopping.
   * Clearing it starts the next basket instead. The order page normally does
   * this the moment checkout lands; this catches the checkout whose order page
   * never loaded (tab closed, connection dropped).
   */
  const commitServerCart = useCallback(
    (seq: number, next: Cart) => {
      if (next.checkedOut) {
        // Only if this is still the cart we are holding. A slow read started
        // before a reorder saved its new cart id would otherwise clear that
        // id — throwing away the basket the reorder just built, because a cart
        // abandoned two screens ago came back checked out.
        if (loadCartId() === next.cartId) {
          clearCartId();
          clearCartPrices();
          commitFetched(seq, null);
        }
        return;
      }
      commitFetched(seq, next);
    },
    [commitFetched],
  );

  // Hydrate from a persisted cartId on mount.
  useEffect(() => {
    const id = loadCartId();
    if (!id) return;
    setLoading(true);
    const seq = stamp();
    readCart(id)
      .then((cart) => {
        // null: a write overlapped this read, and its reply is the newer one.
        if (cart) commitServerCart(seq, cart);
      })
      .catch(() => {
        // Stale/expired cartId — forget it and start fresh on next add. Only
        // if it is still the id we read, though: a reorder may have saved its
        // own since, and that one is not the one that just failed.
        if (loadCartId() !== id) return;
        clearCartId();
        commitFetched(seq, null);
      })
      .finally(() => setLoading(false));
  }, [commitFetched, commitServerCart, readCart, stamp]);

  // Forget the cart when the session ends (logout, or a failed token refresh
  // clearing it). Now that carts follow the ACCOUNT — owned at creation and
  // recoverable via /carts/mine on the next login — keeping the id in this
  // browser would only hand the previous user's basket to whoever uses a
  // shared device next. Nothing is lost: the server still holds the cart.
  useEffect(() => {
    const onAuthChanged = () => {
      if (!loadAuth()) {
        clearCartId();
        commit(null);
      }
    };
    window.addEventListener('tb:auth-changed', onAuthChanged);
    return () => window.removeEventListener('tb:auth-changed', onAuthChanged);
  }, [commit]);

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
      commitFetched(stamp(), null);
      return;
    }
    setLoading(true);
    const seq = stamp();
    try {
      const cart = await readCart(id);
      if (cart) commitServerCart(seq, cart);
    } catch {
      if (loadCartId() === id) {
        clearCartId();
        commitFetched(seq, null);
      }
    } finally {
      setLoading(false);
    }
  }, [commitFetched, commitServerCart, readCart, stamp]);

  /**
   * Send one variant's coalesced quantity. Reads the target at the moment of
   * sending, so every tap up to then is included in a single request.
   */
  const flush = useCallback(
    (variantId: string): Promise<void> => {
      timers.current.delete(variantId);
      return enqueue(async () => {
        const target = targetsRef.current.get(variantId);
        if (target === undefined) return;
        const seq = stamp();
        let failed = false;
        beginWrite();
        let updated: Cart;
        try {
          const id = await ensureCartId();
          const line = cartRef.current?.items.find((i) => i.variantId === variantId);
          updated = line
            ? await updateCartItem(id, line.itemId, target)
            : await addCartItem(id, variantId, target);

          // Clear the pending target only if it is still the number we just
          // sent. A tap during the request left a newer one, which must survive
          // so the display does not jump backwards to the quantity the server
          // confirmed — its own flush is already queued behind this one.
          if (targetsRef.current.get(variantId) === target) {
            targetsRef.current.delete(variantId);
            publishTargets();
          }
          commitFetched(seq, updated);
          // The price the customer was looking at when they chose the quantity
          // is the baseline a later admin edit is measured against.
          acceptVariantPrice(updated, variantId);
        } catch (err) {
          // The server refused (no stock left, line gone). Its cart is the
          // truth: drop the optimistic target, resync, and tell the control why.
          // endWrite() first: this write is over, and the refresh below has to
          // be allowed to commit what it reads.
          endWrite();
          failed = true;
          targetsRef.current.delete(variantId);
          publishTargets();
          const outOfStock = err instanceof ApiError && err.status === 409;
          errorsRef.current.set(variantId, outOfStock ? 'stock' : 'failed');
          publishErrors();
          await refresh();
        } finally {
          if (!failed) endWrite();
        }
      });
    },
    [
      beginWrite,
      commitFetched,
      endWrite,
      enqueue,
      ensureCartId,
      publishErrors,
      publishTargets,
      refresh,
      stamp,
    ],
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

  /**
   * Send every coalesced change now, without waiting out its debounce.
   *
   * <p>Called when the page is being hidden or torn down. A tap in the last
   * 250 ms before the customer switches apps or closes the tab would otherwise
   * be dropped on the floor: its timer dies with the page. `pagehide` rather
   * than `beforeunload` because iOS Safari never fires the latter, and
   * `visibilitychange` as well because a backgrounded tab can be discarded
   * without either. The request may still be cut short if the tab is killed
   * instantly — this narrows the window, it cannot close it — but the customer
   * is far likelier to keep what they tapped than to lose it.
   */
  const flushAllPending = useCallback(() => {
    [...targetsRef.current.keys()].forEach((variantId) => {
      const timer = timers.current.get(variantId);
      if (timer) clearTimeout(timer);
      void flush(variantId);
    });
  }, [flush]);

  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === 'hidden') flushAllPending();
    };
    window.addEventListener('pagehide', flushAllPending);
    document.addEventListener('visibilitychange', onHide);
    return () => {
      window.removeEventListener('pagehide', flushAllPending);
      document.removeEventListener('visibilitychange', onHide);
      flushAllPending();
    };
  }, [flushAllPending]);

  const setQty = useCallback(
    async (itemId: string, qty: number): Promise<Cart> => {
      const id = loadCartId();
      if (!id) throw new Error('No cart');
      const prev = cartRef.current;
      const line = prev?.items.find((i) => i.itemId === itemId);
      if (prev && line) commit(withVariantQty(prev, line.variantId, qty));
      const seq = stamp();
      beginWrite();
      try {
        const updated = await updateCartItem(id, itemId, qty);
        commitFetched(seq, updated);
        return updated;
      } catch (err) {
        if (prev) commit(prev);
        throw err;
      } finally {
        endWrite();
      }
    },
    [beginWrite, commit, commitFetched, endWrite, stamp],
  );

  const removeItem = useCallback(
    async (itemId: string): Promise<Cart> => {
      const id = loadCartId();
      if (!id) throw new Error('No cart');
      const seq = stamp();
      beginWrite();
      try {
        const updated = await removeCartItem(id, itemId);
        commitFetched(seq, updated);
        return updated;
      } finally {
        endWrite();
      }
    },
    [beginWrite, commitFetched, endWrite, stamp],
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
      setQty,
      removeItem,
      refresh,
      reset,
      acknowledgePriceChanges,
    }),
    [nudgeVariant, setQty, removeItem, refresh, reset, acknowledgePriceChanges],
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
