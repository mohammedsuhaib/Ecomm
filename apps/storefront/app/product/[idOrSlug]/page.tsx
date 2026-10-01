import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getLocale, getTranslations } from 'next-intl/server';
import { getProduct, getProducts } from '@/app/lib/api';
import { productDisplayName } from '@/app/lib/productName';
import { cheapestBuyableVariant } from '@/app/lib/variants';
import VegMarker from '@/app/components/VegMarker';
import PriceTag from '@/app/components/PriceTag';
import AddToCartButton from '@/app/components/AddToCartButton';
import JsonLd from '@/app/components/JsonLd';
import ProductCard from '@/app/components/ProductCard';
import ProductThumb from '@/app/components/ProductThumb';
import { absoluteUrl } from '@/app/lib/site';
import { breadcrumbJsonLd, productJsonLd } from '@/app/lib/structuredData';
import type { Product } from '@/app/lib/types';

export const revalidate = 60;

interface Params {
  params: { idOrSlug: string };
}

const SIMILAR_COUNT = 8;

/**
 * Other products from the same category, for the "Similar items" row. Only ones
 * that can be bought right now: pointing someone who is looking at an
 * out-of-stock item at more out-of-stock items is not a suggestion. The
 * fetch is a little larger than the row so filtering still leaves it full, and
 * it is best-effort — a failure just means no row, never a broken product page.
 */
async function similarProducts(product: Product): Promise<Product[]> {
  const page = await getProducts(product.categoryId, 0, SIMILAR_COUNT * 2).catch(() => null);
  return (page?.content ?? [])
    .filter((p) => p.id !== product.id && cheapestBuyableVariant(p) !== null)
    .slice(0, SIMILAR_COUNT);
}

export async function generateMetadata({ params }: Params) {
  const t = await getTranslations('metadata');
  const locale = await getLocale();
  const product = await getProduct(params.idOrSlug).catch(() => null);
  if (!product) return { title: t('productFallback') };
  const name = productDisplayName(product, locale);
  const title = t('productTitle', { name });
  // Always the slug, never the numeric id: both resolve here, and without a
  // canonical the same product would be two indexable URLs competing with
  // each other. The slug is the one a person would recognise in a result.
  const canonical = `/product/${product.slug}`;
  return {
    title,
    description: product.description,
    alternates: { canonical },
    openGraph: {
      type: 'website',
      title,
      description: product.description,
      url: canonical,
      ...(product.imageUrl ? { images: [{ url: absoluteUrl(product.imageUrl) }] } : {}),
    },
  };
}

export default async function ProductPage({ params }: Params) {
  const product = await getProduct(params.idOrSlug);
  if (!product) notFound();

  const t = await getTranslations('product');
  const tc = await getTranslations('common');
  const locale = await getLocale();
  const displayName = productDisplayName(product, locale);
  const similar = await similarProducts(product);
  // Product is on and has variants, but none are sellable right now.
  const outOfStock =
    product.available &&
    product.variants.length > 0 &&
    !product.variants.some((v) => v.available && v.availableStock > 0);

  return (
    <>
      {/* Price and availability per variant, from the same live figures the
          Add to cart button reads — so a rich result never offers something
          the basket then refuses. */}
      <JsonLd data={productJsonLd(product, locale)} />
      <JsonLd
        data={breadcrumbJsonLd([
          { name: tc('home'), path: '/' },
          { name: displayName, path: `/product/${product.slug}` },
        ])}
      />

      <nav className="breadcrumb">
        <Link href="/">{tc('home')}</Link> / <span>{displayName}</span>
      </nav>

      <article className="product-detail">
        <div className="hero-img">
          <ProductThumb product={product} />
        </div>

        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
            <VegMarker veg={product.vegMarker} />
            {displayName}
          </h1>
          {!product.available && (
            <p className="notice error">{t('unavailableNotice')}</p>
          )}
          {outOfStock && (
            <p className="notice error">{t('outOfStockNotice')}</p>
          )}
          {product.description && (
            <p className="muted">{product.description}</p>
          )}

          <h2 className="section-title" style={{ fontSize: '1.05rem' }}>
            {t('availableSizes')}
          </h2>
          {product.variants.length > 0 ? (
            <ul className="variant-list">
              {product.variants.map((v) => (
                <li key={v.id} className="variant-row">
                  <span>
                    <span className="label">{v.label}</span>
                    <br />
                    <PriceTag sellingPrice={v.sellingPrice} mrp={v.mrp} />
                  </span>
                  <AddToCartButton variant={v} productName={displayName} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="empty-state">{t('noSizes')}</p>
          )}
        </div>
      </article>

      {similar.length > 0 && (
        <section>
          <h2 className="section-title">{t('similarItems')}</h2>
          <div className="product-grid">
            {similar.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        </section>
      )}
    </>
  );
}
