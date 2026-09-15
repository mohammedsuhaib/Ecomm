import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { getStore } from '@/app/lib/api';
import type { Store } from '@/app/lib/types';

/** Where the published policy pages are served (see holding-site/). */
const POLICY_BASE = 'https://town-basket.com';

const POLICY_PAGES = [
  { key: 'terms', file: 'terms.html' },
  { key: 'privacy', file: 'privacy.html' },
  { key: 'refund', file: 'refund.html' },
  { key: 'shipping', file: 'shipping.html' },
  { key: 'contact', file: 'contact.html' },
] as const;

/**
 * Site footer with brand line, a few navigation links, and the store's phone
 * number when one is published.
 *
 * <p>The number is here because it is the app's only route to a human: copy
 * elsewhere asks the customer to get in touch when something (a cancellation
 * that came too late, say) can no longer be fixed on screen. With no number
 * set, the footer simply omits it rather than offering a link to nowhere —
 * staff add one on the admin store card.
 *
 * <p>The store fetch is tolerated failing, exactly as the home page does: the
 * footer renders on every page and must never be what takes one down.
 */
export default async function Footer() {
  const t = await getTranslations('footer');
  const tc = await getTranslations('common');
  const store = await getStore().catch(() => null as Store | null);
  const phone = store?.supportPhone?.trim() || null;
  const year = new Date().getFullYear();
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <nav aria-label={t('ariaLabel')}>
          <Link href="/">{tc('home')}</Link>
          <Link href="/search?q=">{tc('search')}</Link>
          {phone && (
            <a href={`tel:${phone.replace(/[^+\d]/g, '')}`}>
              {t('callStore', { phone })}
            </a>
          )}
        </nav>
        {/* The shop's published policies. They have always existed on
            town-basket.com but nothing in the app linked them, while the
            cancellation copy cited the refund policy by name — so a customer
            was pointed at a document they had no way to open. Absolute URLs
            because these live on the marketing site, not this app. */}
        <nav aria-label={t('policies')} className="footer-policies">
          {POLICY_PAGES.map(({ key, file }) => (
            <a
              key={key}
              href={`${POLICY_BASE}/${file}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t(key)}
            </a>
          ))}
        </nav>
        <div className="copyright">
          {t('copyright', { year })}
        </div>
      </div>
    </footer>
  );
}
