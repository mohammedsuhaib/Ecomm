// Client-side cart persistence. The cart itself lives server-side (keyed by
// cartId); we only persist the cartId in localStorage so it survives reloads
// and navigation. A cart is created lazily on the first "add to cart".

export const CART_ID_KEY = 'tb.cartId.v1';

export function loadCartId(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage.getItem(CART_ID_KEY) || null;
  } catch {
    return null;
  }
}

export function saveCartId(cartId: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(CART_ID_KEY, cartId);
  } catch {
    /* storage may be unavailable (private mode); cart still works in-session */
  }
}

export function clearCartId(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(CART_ID_KEY);
  } catch {
    /* ignore */
  }
}
