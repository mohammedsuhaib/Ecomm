import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { searchProducts } from '@/app/lib/api';
import ProductCard from '@/app/components/ProductCard';
import SortControl from '@/app/components/SortControl';
import { parseSort } from '@/app/lib/sort';

// Search results depend on the query; render dynamically (no static cache).
export const dynamic = 'force-dynamic';

interface Params {
  searchParams: { q?: string; page?: string; sort?: string };
}

export async function generateMetadata({ searchParams }: Params) {
  const t = await getTranslations('metadata');
  const q = (searchParams.q ?? '').trim();
  return { title: q ? t('searchTitle', { q }) : t('searchFallback') };
}

export default async function SearchPage({ searchParams }: Params) {
  const q = (searchParams.q ?? '').trim();
  const page = Math.max(0, Number.parseInt(searchParams.page ?? '0', 10) || 0);
  const sort = parseSort(searchParams.sort);
  const sortQs = sort ? `&sort=${sort}` : '';
  const t = await getTranslations('search');
  const tc = await getTranslations('common');

  if (!q) {
    return (
      <p className="empty-state">
        {t('prompt')}
      </p>
    );
  }

  const resultPage = await searchProducts(q, page, 24, { sort }).catch(
    () => null,
  );
  const products = resultPage?.content ?? [];
  const total = resultPage?.totalElements ?? products.length;
  const size = resultPage?.size ?? 24;
  const hasNext = (page + 1) * size < total;

  return (
    <>
      <div className="listing-head">
        <h1 className="section-title">
          {t('resultsFor', { q })}{' '}
          <span
            className="muted"
            style={{ fontSize: '0.9rem', fontWeight: 400 }}
          >
            {resultPage ? t('resultsCount', { count: total }) : ''}
          </span>
        </h1>
        <SortControl basePath="/search" sort={sort} />
      </div>

      {products.length > 0 ? (
        <>
          <div className="product-grid">
            {products.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              marginTop: '1.5rem',
              gap: '1rem',
            }}
          >
            {page > 0 ? (
              <Link
                className="btn btn-outline"
                href={`/search?q=${encodeURIComponent(q)}&page=${page - 1}${sortQs}`}
              >
                {tc('previous')}
              </Link>
            ) : (
              <span />
            )}
            {hasNext && (
              <Link
                className="btn btn-outline"
                href={`/search?q=${encodeURIComponent(q)}&page=${page + 1}${sortQs}`}
              >
                {tc('next')}
              </Link>
            )}
          </div>
        </>
      ) : (
        <p className="empty-state">
          {t('noMatch', { q })}
        </p>
      )}
    </>
  );
}
