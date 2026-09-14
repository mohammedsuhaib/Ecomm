'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { ApiError, getStore } from '@/app/lib/api';
import { formatRupees, subtractRupees } from '@/app/lib/format';
import type { CartItem } from '@/app/lib/types';
import { useCart } from '@/app/components/CartProvider';
import PriceChangeNotice from '@/app/components/PriceChangeNotice';
import CartEmptyState from '@/app/components/CartEmptyState';

export default function CartPage() {
  const t = useTranslations('cart');
  const tc = useTranslations('common');
  const { cart, loading, refresh, setQty, removeItem, priceChanges } = useCart();
  const [minOrderValue, setMinOrderValue] = useState<number | null>(null);
  // Server-decided (its clock, not the device's): the shop is shut, so
  // checkout would refuse the order. Block "Proceed to checkout" here rather
  // than letting the customer fill the whole checkout form for nothing.
  const [storeClosed, setStoreClosed] = useState(false);
  const [busyItem, setBusyItem] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Re-fetch the cart on mount so prices/stock are current.
  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Minimum-order threshold and open/closed both come from store config. Fetch
  // it live (noStore) so a closure that just happened is seen, not a cached
  // "open". Strictly `open === false` only — a missing field (older API) or a
  // failed fetch must not block a customer who could order (see StoreClosedBanner).
  useEffect(() => {
    getStore({ noStore: true })
      .then((s) => {
        setMinOrderValue(s.minOrderValue);
        setStoreClosed(s.open === false);
      })
      .catch(() => {
        setMinOrderValue(null);
        setStoreClosed(false);
      });
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
          ? t('limitedStock', { product: item.productName })
          : t('couldNotUpdate'),
      );
    } finally {
      setBusyItem(null);
    }
  }

  const items = cart?.items ?? [];
  const subtotal = cart?.subtotal ?? 0;
  const hasUnavailable = items.some((i) => !i.available);
  const hasShortage = items.some((i) => i.available && i.availableStock < i.qty);
  const belowMin = minOrderValue != null && subtotal < minOrderValue;
  const canCheckout =
    items.length > 0 &&
    !storeClosed &&
    // A line repriced under the customer must be accepted first, not carried
    // into checkout inside a total they were never told had changed.
    priceChanges.length === 0 &&
    !belowMin &&
    !hasUnavailable &&
    !hasShortage &&
    !busyItem;

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">{tc('home')}</Link> / <span>{tc('cart')}</span>
      </nav>

      <h1 className="section-title" style={{ marginTop: 0 }}>
        {t('title')}
      </h1>

      {error && <p className="notice error">{error}</p>}

      {loading && items.length === 0 ? (
        <div aria-busy="true" aria-label={t('loading')}>
          <div className="skeleton-row" />
          <div className="skeleton-row" />
          <div className="skeleton-row" />
        </div>
      ) : items.length === 0 ? (
        <CartEmptyState />
      ) : (
        <>
          <ul className="cart-list">
            {items.map((item) => (
              <li key={item.itemId} className="cart-row">
                <div className="cart-row-info">
                  <span className="cart-row-name">{item.productName}</span>
                  <span className="muted cart-row-label">{item.label}</span>
                  <span className="muted">
                    {t('each', { price: formatRupees(item.unitPrice) })}
                  </span>
                  {!item.available ? (
                    <span className="unavailable-tag">
                      {t('noLongerAvailableRemove')}
                    </span>
                  ) : item.availableStock < item.qty ? (
                    <span className="unavailable-tag">
                      {item.availableStock > 0
                        ? t('onlyNLeft', { count: item.availableStock })
                        : t('outOfStockRemove')}
                    </span>
                  ) : null}
                </div>
                <div className="cart-row-actions">
                  <div className="qty-stepper" aria-label={t('ariaQuantity', { product: item.productName })}>
                    <button
                      type="button"
                      disabled={busyItem === item.itemId}
                      onClick={() => change(item, item.qty - 1)}
                      aria-label={tc('decrease')}
                    >
                      −
                    </button>
                    <span className="qty-value">{item.qty}</span>
                    <button
                      type="button"
                      disabled={
                        busyItem === item.itemId ||
                        item.qty >= item.availableStock
                      }
                      title={
                        item.qty >= item.availableStock
                          ? t('onlyNLeft', { count: item.availableStock })
                          : undefined
                      }
                      onClick={() => change(item, item.qty + 1)}
                      aria-label={tc('increase')}
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
                    {tc('remove')}
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <div className="cart-summary">
            <div className="cart-summary-row">
              <span>{t('subtotal')}</span>
              <strong>{formatRupees(subtotal)}</strong>
            </div>

            {belowMin && minOrderValue != null && (
              <p className="notice warn">
                {t('minOrderNotice', {
                  min: formatRupees(minOrderValue),
                  needed: formatRupees(subtractRupees(minOrderValue, subtotal)),
                })}
              </p>
            )}
            {hasUnavailable && (
              <p className="notice error">
                {t('someNoLongerAvailable')}
              </p>
            )}
            {hasShortage && !hasUnavailable && (
              <p className="notice error">
                {t('someShortage')}
              </p>
            )}
            {storeClosed && (
              <p className="notice warn">{t('storeClosedNotice')}</p>
            )}
            <PriceChangeNotice />

            {canCheckout ? (
              <Link href="/checkout" className="btn btn-block">
                {t('proceedToCheckout')}
              </Link>
            ) : (
              <button type="button" className="btn btn-block" disabled>
                {t('proceedToCheckout')}
              </button>
            )}
            <Link href="/" className="btn btn-outline btn-block">
              {tc('continueShopping')}
            </Link>
          </div>
        </>
      )}
    </>
  );
}
