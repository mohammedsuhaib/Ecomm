import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getTranslations } from 'next-intl/server';
import { getCategories, getProducts } from '@/app/lib/api';
import ProductCard from '@/app/components/ProductCard';
import SortControl from '@/app/components/SortControl';
import { parseSort } from '@/app/lib/sort';
import type { Category } from '@/app/lib/types';

export const revalidate = 60;

interface Params {
  params: { slug: string };
  searchParams: { page?: string; sort?: string };
}

export async function generateMetadata({ params }: Params) {
  const t = await getTranslations('metadata');
  const categories = await getCategories().catch(() => [] as Category[]);
  const cat = categories.find((c) => c.slug === params.slug);
  return {
    title: cat ? t('categoryTitle', { name: cat.name }) : t('categoryFallback'),
  };
}

export default async function CategoryPage({ params, searchParams }: Params) {
  const page = Math.max(0, Number.parseInt(searchParams.page ?? '0', 10) || 0);
  const sort = parseSort(searchParams.sort);
  const sortQs = sort ? `&sort=${sort}` : '';
  const tc = await getTranslations('common');
  const t = await getTranslations('category');

  // Resolve slug -> category id (the products endpoint filters by id).
  const categories = await getCategories().catch(() => [] as Category[]);
  const category = categories.find((c) => c.slug === params.slug);
  if (!category) notFound();

  const productsPage = await getProducts(category.id, page, 24, { sort }).catch(
    () => null,
  );
  const products = productsPage?.content ?? [];
  const total = productsPage?.totalElements ?? products.length;
  const size = productsPage?.size ?? 24;
  const hasNext = (page + 1) * size < total;

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">{tc('home')}</Link> / <span>{category.name}</span>
      </nav>
      <div className="listing-head">
        <h1 className="section-title">{category.name}</h1>
        <SortControl basePath={`/category/${category.slug}`} sort={sort} />
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
                href={`/category/${category.slug}?page=${page - 1}${sortQs}`}
              >
                {tc('previous')}
              </Link>
            ) : (
              <span />
            )}
            {hasNext && (
              <Link
                className="btn btn-outline"
                href={`/category/${category.slug}?page=${page + 1}${sortQs}`}
              >
                {tc('next')}
              </Link>
            )}
          </div>
        </>
      ) : (
        <p className="empty-state">{t('noProducts')}</p>
      )}
    </>
  );
}
