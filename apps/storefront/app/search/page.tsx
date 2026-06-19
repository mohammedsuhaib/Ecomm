import Link from 'next/link';
import { searchProducts } from '@/app/lib/api';
import ProductCard from '@/app/components/ProductCard';
import SortControl from '@/app/components/SortControl';
import { parseSort } from '@/app/lib/sort';

// Search results depend on the query; render dynamically (no static cache).
export const dynamic = 'force-dynamic';

interface Params {
  searchParams: { q?: string; page?: string; sort?: string };
}

export function generateMetadata({ searchParams }: Params) {
  const q = (searchParams.q ?? '').trim();
  return { title: q ? `“${q}” — Town Basket` : 'Search — Town Basket' };
}

export default async function SearchPage({ searchParams }: Params) {
  const q = (searchParams.q ?? '').trim();
  const page = Math.max(0, Number.parseInt(searchParams.page ?? '0', 10) || 0);
  const sort = parseSort(searchParams.sort);
  const sortQs = sort ? `&sort=${sort}` : '';

  if (!q) {
    return (
      <p className="empty-state">
        Type a product name in the search bar above to find groceries.
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
          Results for “{q}”{' '}
          <span
            className="muted"
            style={{ fontSize: '0.9rem', fontWeight: 400 }}
          >
            {resultPage ? `(${total})` : ''}
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
                ← Previous
              </Link>
            ) : (
              <span />
            )}
            {hasNext && (
              <Link
                className="btn btn-outline"
                href={`/search?q=${encodeURIComponent(q)}&page=${page + 1}${sortQs}`}
              >
                Next →
              </Link>
            )}
          </div>
        </>
      ) : (
        <p className="empty-state">
          No products matched “{q}”. Try a different search.
        </p>
      )}
    </>
  );
}
