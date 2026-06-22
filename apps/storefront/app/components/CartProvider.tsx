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
  addCartItem,
  createCart,
  getCart,
  removeCartItem,
  updateCartItem,
} from '@/app/lib/api';
import { clearCartId, loadCartId, saveCartId } from '@/app/lib/cart';
import type { Cart } from '@/app/lib/types';

// Cart state shared across the storefront shell: the header badge, the
// AddToCartButton, and the cart page all read/mutate through this context.
// The server is the source of truth; every mutation returns the fresh cart.
interface CartContextValue {
  cart: Cart | null;
  itemCount: number;
  loading: boolean;
  /** Ensure a cart exists, add a variant, and refresh state. Returns the cart. */
  addItem: (variantId: string, qty: number) => Promise<Cart>;
  /** Set a line's quantity (0 removes). */
  setQty: (itemId: string, qty: number) => Promise<Cart>;
  /** Decrement a variant's quantity by one (0 removes), resolving its line. */
  decrementVariant: (variantId: string) => Promise<Cart>;
  /** Remove a line entirely. */
  removeItem: (itemId: string) => Promise<Cart>;
  /** Re-fetch the cart from the server (e.g. on cart-page mount). */
  refresh: () => Promise<void>;
  /** Quantity of a given variant currently in the cart (for inline controls). */
  qtyOf: (variantId: string) => number;
  /** Forget the local cart (called after a successful order). */
  reset: () => void;
}

const CartContext = createContext<CartContextValue | null>(null);

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within <CartProvider>');
  return ctx;
}

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

export default function CartProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(false);
  // Guards lazy cart creation against concurrent first-adds.
  const creating = useRef<Promise<string> | null>(null);

  // Hydrate from a persisted cartId on mount.
  useEffect(() => {
    const id = loadCartId();
    if (!id) return;
    setLoading(true);
    getCart(id)
      .then(setCart)
      .catch(() => {
        // Stale/expired cartId — forget it and start fresh on next add.
        clearCartId();
        setCart(null);
      })
      .finally(() => setLoading(false));
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

  const addItem = useCallback(
    async (variantId: string, qty: number): Promise<Cart> => {
      const id = await ensureCartId();
      // Optimistic increment when the line already exists (the common stepper
      // case); a brand-new line has no known price, so wait for the server.
      const prev = cart;
      const existing = prev?.items.find((i) => i.variantId === variantId);
      if (prev && existing) {
        setCart(withVariantQty(prev, variantId, existing.qty + qty));
      }
      try {
        const updated = await addCartItem(id, variantId, qty);
        setCart(updated);
        return updated;
      } catch (err) {
        if (prev) setCart(prev); // roll back the optimistic change
        throw err;
      }
    },
    [ensureCartId, cart],
  );

  const setQty = useCallback(
    async (itemId: string, qty: number): Promise<Cart> => {
      const id = loadCartId();
      if (!id) throw new Error('No cart');
      const prev = cart;
      const line = prev?.items.find((i) => i.itemId === itemId);
      if (prev && line) setCart(withVariantQty(prev, line.variantId, qty));
      try {
        const updated = await updateCartItem(id, itemId, qty);
        setCart(updated);
        return updated;
      } catch (err) {
        if (prev) setCart(prev);
        throw err;
      }
    },
    [cart],
  );

  const decrementVariant = useCallback(
    async (variantId: string): Promise<Cart> => {
      const id = loadCartId();
      if (!id) throw new Error('No cart');
      const prev = cart;
      const line = prev?.items.find((i) => i.variantId === variantId);
      if (!line) throw new Error('Variant not in cart');
      setCart(withVariantQty(prev!, variantId, line.qty - 1));
      try {
        const updated = await updateCartItem(id, line.itemId, line.qty - 1);
        setCart(updated);
        return updated;
      } catch (err) {
        if (prev) setCart(prev);
        throw err;
      }
    },
    [cart],
  );

  const removeItem = useCallback(async (itemId: string): Promise<Cart> => {
    const id = loadCartId();
    if (!id) throw new Error('No cart');
    const updated = await removeCartItem(id, itemId);
    setCart(updated);
    return updated;
  }, []);

  const refresh = useCallback(async (): Promise<void> => {
    const id = loadCartId();
    if (!id) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      setCart(await getCart(id));
    } catch {
      clearCartId();
      setCart(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const reset = useCallback(() => {
    clearCartId();
    setCart(null);
  }, []);

  const qtyOf = useCallback(
    (variantId: string): number =>
      cart?.items.find((i) => i.variantId === variantId)?.qty ?? 0,
    [cart],
  );

  const value = useMemo<CartContextValue>(
    () => ({
      cart,
      itemCount: cart?.itemCount ?? 0,
      loading,
      addItem,
      setQty,
      decrementVariant,
      removeItem,
      refresh,
      qtyOf,
      reset,
    }),
    [
      cart,
      loading,
      addItem,
      setQty,
      decrementVariant,
      removeItem,
      refresh,
      qtyOf,
      reset,
    ],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
