import { useTranslations } from 'next-intl';
import { formatRupees } from '@/app/lib/format';

/**
 * The bill, laid out the way a shopper reads a receipt: what the items would
 * have cost at shelf prices (struck through) beside what they cost here, the
 * delivery fee, and what to pay, with a green "Savings on this order" card
 * under it when something was discounted.
 *
 * <p>Used on the cart, the checkout form and the order page so all three read
 * the same. {@code savings} comes from the API (the sum of mrp minus price over
 * discounted lines), and the shelf-price total is derived from it, so the two can
 * never disagree. Delivery is free on every order, which is why that row is not a
 * number.
 */
export default function BillSummary({
  subtotal,
  savings,
  total = subtotal,
}: {
  subtotal: number;
  savings: number;
  /** What the customer pays; equals the subtotal because delivery is free. */
  total?: number;
}) {
  const t = useTranslations('bill');
  const saved = savings > 0 ? savings : 0;
  const shelfTotal = Math.round((subtotal + saved) * 100) / 100;

  return (
    <>
      <section className="bill-card" aria-label={t('title')}>
        <h2 className="bill-title">{t('title')}</h2>
        <div className="bill-row">
          <span>{t('itemTotal')}</span>
          <span className="bill-amount">
            {saved > 0 && <s className="bill-struck">{formatRupees(shelfTotal)}</s>}
            <span>{formatRupees(subtotal)}</span>
          </span>
        </div>
        <div className="bill-row">
          <span>{t('delivery')}</span>
          <strong className="bill-free">{t('free')}</strong>
        </div>
        <div className="bill-row bill-total">
          <span>{t('toPay')}</span>
          <strong>{formatRupees(total)}</strong>
        </div>
      </section>

      {saved > 0 && (
        <section className="savings-card" aria-label={t('savingsTitle')}>
          <div className="savings-head">
            <h2>{t('savingsTitle')}</h2>
            <span className="savings-badge">{formatRupees(saved)}</span>
          </div>
          <div className="savings-inner">
            <span>{t('mrpDiscount')}</span>
            <strong>{formatRupees(saved)}</strong>
          </div>
        </section>
      )}
    </>
  );
}
