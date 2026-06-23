import Link from 'next/link';
import { useTranslations } from 'next-intl';

export default function NotFound() {
  const t = useTranslations('notFound');
  const tc = useTranslations('common');
  return (
    <div className="empty-state">
      <div style={{ fontSize: '3rem' }} aria-hidden>
        🧺
      </div>
      <h1 className="section-title">{t('title')}</h1>
      <p>{t('body')}</p>
      <p>
        <Link className="btn" href="/">
          {tc('backToShopping')}
        </Link>
      </p>
    </div>
  );
}
