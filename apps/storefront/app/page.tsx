import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { getCategories, getProducts, getStore } from './lib/api';
import { formatHours, formatRupees } from './lib/format';
import CategoryCard from './components/CategoryCard';
import ProductCard from './components/ProductCard';
import SortControl from './components/SortControl';
import { parseSort } from './lib/sort';
import type { Category, Product, Store } from './lib/types';

// SSR with ISR: fast first paint + SEO-indexable, revalidated periodically.
export const revalidate = 60;

export default async function Home({
  searchParams,
}: {
  searchParams: { sort?: string };
}) {
  const sort = parseSort(searchParams.sort);
  const t = await getTranslations('home');

  // Fetch in parallel; tolerate a backend that isn't up yet (M2 dev) so the
  // shell still renders rather than throwing.
  const [categories, productsPage, featuredPage, store] = await Promise.all([
    getCategories().catch(() => [] as Category[]),
    getProducts(undefined, 0, 12, { sort }).catch(() => null),
    getProducts(undefined, 0, 12, { featured: true }).catch(() => null),
    getStore().catch(() => null as Store | null),
  ]);

  const products: Product[] = productsPage?.content ?? [];
  const featured: Product[] = featuredPage?.content ?? [];

  return (
    <>
      <section
        className="notice"
        style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}
      >
        <span>
          🧺 <strong>{t('bannerTitle')}</strong>{' '}
          {t('bannerOrderWithin', { store: store?.name ?? t('storeFallback') })}
        </span>
        {store && (
          <span className="muted">
            {t('openMinOrder', {
              hours: formatHours(store.openingTime, store.closingTime),
              amount: formatRupees(store.minOrderValue),
            })}
          </span>
        )}
      </section>

      {featured.length > 0 && (
        <>
          <h2 className="section-title">{t('popularPicks')}</h2>
          <div className="product-grid">
            {featured.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        </>
      )}

      <h1 className="section-title">{t('shopByCategory')}</h1>
      {categories.length > 0 ? (
        <div className="category-grid">
          {categories.map((c) => (
            <CategoryCard key={c.id} category={c} />
          ))}
        </div>
      ) : (
        <p className="empty-state">
          {t('categoriesEmpty')}
        </p>
      )}

      <div className="listing-head">
        <h2 className="section-title">{t('popularProducts')}</h2>
        <SortControl basePath="/" sort={sort} />
      </div>
      {products.length > 0 ? (
        <div className="product-grid">
          {products.map((p) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      ) : (
        <p className="empty-state">
          {t('productsEmpty')}{' '}
          <Link href="/">{t('reload')}</Link>
        </p>
      )}
    </>
  );
}
