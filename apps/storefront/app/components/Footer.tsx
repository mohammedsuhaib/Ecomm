import Link from 'next/link';
import { useTranslations } from 'next-intl';

/** Site footer with brand line and a few navigation links. */
export default function Footer() {
  const t = useTranslations('footer');
  const tc = useTranslations('common');
  const year = new Date().getFullYear();
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <nav aria-label={t('ariaLabel')}>
          <Link href="/">{tc('home')}</Link>
          <Link href="/search?q=">{tc('search')}</Link>
        </nav>
        <div className="copyright">
          {t('copyright', { year })}
        </div>
      </div>
    </footer>
  );
}
