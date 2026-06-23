import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { getTranslations } from 'next-intl/server';

// Offline fallback served by the service worker when a navigation request
// can't be fulfilled from cache or network (Serwist, see app/sw.ts).
export async function generateMetadata() {
  const t = await getTranslations('metadata');
  return { title: t('offlineTitle') };
}

export default function OfflinePage() {
  const t = useTranslations('offline');
  return (
    <div className="empty-state">
      <div style={{ fontSize: '3rem' }} aria-hidden>
        📶
      </div>
      <h1 className="section-title">{t('title')}</h1>
      <p>
        {t('body')}
      </p>
      <p>
        <Link className="btn" href="/">
          {t('tryAgain')}
        </Link>
      </p>
    </div>
  );
}
