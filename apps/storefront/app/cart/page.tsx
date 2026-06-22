'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { ApiError, getStore } from '@/app/lib/api';
import { formatRupees, subtractRupees } from '@/app/lib/format';
import type { CartItem } from '@/app/lib/types';
import { useCart } from '@/app/components/CartProvider';

export default function CartPage() {
  const t = useTranslations('cart');
  const tc = useTranslations('common');
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
        <p className="empty-state">{t('loading')}</p>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <p>{t('empty')}</p>
          <Link href="/" className="btn">
            {tc('startShopping')}
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
                      disabled={busyItem === item.itemId}
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
