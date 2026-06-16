'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError, getStore } from '@/app/lib/api';
import { formatRupees } from '@/app/lib/format';
import type { CartItem } from '@/app/lib/types';
import { useCart } from '@/app/components/CartProvider';

export default function CartPage() {
  const { cart, loading, refresh, setQty, removeItem } = useCart();
  const [minOrderValue, setMinOrderValue] = useState<number | null>(null);
  const [busyItem, setBusyItem] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Re-fetch the cart on mount so prices/stock are current.
  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Minimum-order threshold comes from store config.
  useEffect(() => {
    getStore()
      .then((s) => setMinOrderValue(s.minOrderValue))
      .catch(() => setMinOrderValue(null));
  }, []);

  async function change(item: CartItem, qty: number) {
    setBusyItem(item.itemId);
    setError(null);
    try {
      if (qty <= 0) await removeItem(item.itemId);
      else await setQty(item.itemId, qty);
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 409
          ? `Only limited stock available for ${item.productName}.`
          : 'Could not update your cart. Please try again.',
      );
    } finally {
      setBusyItem(null);
    }
  }

  const items = cart?.items ?? [];
  const subtotal = cart?.subtotal ?? 0;
  const hasUnavailable = items.some((i) => !i.available);
  const belowMin = minOrderValue != null && subtotal < minOrderValue;
  const canCheckout =
    items.length > 0 && !belowMin && !hasUnavailable && !busyItem;

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">Home</Link> / <span>Cart</span>
      </nav>

      <h1 className="section-title" style={{ marginTop: 0 }}>
        🧺 Your cart
      </h1>

      {error && <p className="notice error">{error}</p>}

      {loading && items.length === 0 ? (
        <p className="empty-state">Loading your cart…</p>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <p>Your cart is empty.</p>
          <Link href="/" className="btn">
            Start shopping
          </Link>
        </div>
      ) : (
        <>
          <ul className="cart-list">
            {items.map((item) => (
              <li key={item.itemId} className="cart-row">
                <div className="cart-row-info">
                  <span className="cart-row-name">{item.productName}</span>
                  <span className="muted cart-row-label">{item.label}</span>
                  <span className="muted">
                    {formatRupees(item.unitPrice)} each
                  </span>
                  {!item.available && (
                    <span className="unavailable-tag">
                      Out of stock — remove to continue
                    </span>
                  )}
                </div>
                <div className="cart-row-actions">
                  <div className="qty-stepper" aria-label={`Quantity of ${item.productName}`}>
                    <button
                      type="button"
                      disabled={busyItem === item.itemId}
                      onClick={() => change(item, item.qty - 1)}
                      aria-label="Decrease quantity"
                    >
                      −
                    </button>
                    <span className="qty-value">{item.qty}</span>
                    <button
                      type="button"
                      disabled={busyItem === item.itemId}
                      onClick={() => change(item, item.qty + 1)}
                      aria-label="Increase quantity"
                    >
                      +
                    </button>
                  </div>
                  <span className="cart-row-total">
                    {formatRupees(item.lineTotal)}
                  </span>
                  <button
                    type="button"
                    className="link-danger"
                    disabled={busyItem === item.itemId}
                    onClick={() => change(item, 0)}
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <div className="cart-summary">
            <div className="cart-summary-row">
              <span>Subtotal</span>
              <strong>{formatRupees(subtotal)}</strong>
            </div>

            {belowMin && minOrderValue != null && (
              <p className="notice warn">
                Minimum order is {formatRupees(minOrderValue)}. Add{' '}
                {formatRupees(minOrderValue - subtotal)} more to checkout.
              </p>
            )}
            {hasUnavailable && (
              <p className="notice error">
                Some items are out of stock. Remove them to continue.
              </p>
            )}

            {canCheckout ? (
              <Link href="/checkout" className="btn btn-block">
                Proceed to checkout
              </Link>
            ) : (
              <button type="button" className="btn btn-block" disabled>
                Proceed to checkout
              </button>
            )}
            <Link href="/" className="btn btn-outline btn-block">
              Continue shopping
            </Link>
          </div>
        </>
      )}
    </>
  );
}
