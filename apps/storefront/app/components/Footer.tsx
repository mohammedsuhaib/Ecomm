import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { getStore } from '@/app/lib/api';
import type { Store } from '@/app/lib/types';

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
        <div className="copyright">
          {t('copyright', { year })}
        </div>
      </div>
    </footer>
  );
}
