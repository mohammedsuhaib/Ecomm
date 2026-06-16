import Link from 'next/link';
import { getCategories, getProducts, getStore } from './lib/api';
import { formatRupees } from './lib/format';
import CategoryCard from './components/CategoryCard';
import ProductCard from './components/ProductCard';
import type { Category, Product, Store } from './lib/types';

// SSR with ISR: fast first paint + SEO-indexable, revalidated periodically.
export const revalidate = 60;

export default async function Home() {
  // Fetch in parallel; tolerate a backend that isn't up yet (M2 dev) so the
  // shell still renders rather than throwing.
  const [categories, productsPage, store] = await Promise.all([
    getCategories().catch(() => [] as Category[]),
    getProducts(undefined, 0, 12).catch(() => null),
    getStore().catch(() => null as Store | null),
  ]);

  const products: Product[] = productsPage?.content ?? [];

  return (
    <>
      <section
        className="notice"
        style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}
      >
        <span>
          🧺 <strong>Fresh groceries, delivered fast.</strong> Order within
          5&nbsp;km of {store?.name ?? 'our store'}.
        </span>
        {store && (
          <span className="muted">
            Open {store.openingTime}–{store.closingTime} · Min order{' '}
            {formatRupees(store.minOrderValue)}
          </span>
        )}
      </section>

      <h1 className="section-title">Shop by category</h1>
      {categories.length > 0 ? (
        <div className="category-grid">
          {categories.map((c) => (
            <CategoryCard key={c.id} category={c} />
          ))}
        </div>
      ) : (
        <p className="empty-state">
          Categories will appear here once the catalogue is loaded.
        </p>
      )}

      <div
        style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}
      >
        <h2 className="section-title">Popular products</h2>
      </div>
      {products.length > 0 ? (
        <div className="product-grid">
          {products.map((p) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      ) : (
        <p className="empty-state">
          No products to show yet. The catalogue API may still be starting up —
          try refreshing in a moment.{' '}
          <Link href="/">Reload</Link>
        </p>
      )}
    </>
  );
}
