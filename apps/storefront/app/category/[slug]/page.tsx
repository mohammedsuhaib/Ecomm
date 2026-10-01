import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getLocale, getTranslations } from 'next-intl/server';
import { getCategories, getProducts } from '@/app/lib/api';
import JsonLd from '@/app/components/JsonLd';
import ProductCard from '@/app/components/ProductCard';
import SortControl from '@/app/components/SortControl';
import { categoryDisplayName } from '@/app/lib/productName';
import { parseSort } from '@/app/lib/sort';
import { breadcrumbJsonLd } from '@/app/lib/structuredData';
import type { Category } from '@/app/lib/types';

export const revalidate = 60;

interface Params {
  params: { slug: string };
  searchParams: { page?: string; sort?: string };
}

export async function generateMetadata({ params, searchParams }: Params) {
  const t = await getTranslations('metadata');
  const categories = await getCategories().catch(() => [] as Category[]);
  const cat = categories.find((c) => c.slug === params.slug);
  const page = Math.max(0, Number.parseInt(searchParams.page ?? '0', 10) || 0);
  // Self-referencing, and it keeps the page number: page two is a different
  // set of products and deserves its own entry. It drops `sort`, because a
  // re-ordered list of the same products is the same page — that is the one
  // duplicate this catalogue can actually generate.
  const canonical = `/category/${params.slug}${page > 0 ? `?page=${page}` : ''}`;
  const name = cat ? categoryDisplayName(cat, await getLocale()) : null;
  const title = name ? t('categoryTitle', { name }) : t('categoryFallback');
  return {
    title,
    ...(name ? { description: t('categoryDescription', { name }) } : {}),
    alternates: { canonical },
    openGraph: { title, url: canonical },
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
  const displayName = categoryDisplayName(category, await getLocale());

  const productsPage = await getProducts(category.id, page, 24, { sort }).catch(
    () => null,
  );
  const products = productsPage?.content ?? [];
  const total = productsPage?.totalElements ?? products.length;
  const size = productsPage?.size ?? 24;
  const hasNext = (page + 1) * size < total;

  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: tc('home'), path: '/' },
          { name: displayName, path: `/category/${category.slug}` },
        ])}
      />

      <nav className="breadcrumb">
        <Link href="/">{tc('home')}</Link> / <span>{displayName}</span>
      </nav>
      <div className="listing-head">
        <h1 className="section-title">{displayName}</h1>
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
