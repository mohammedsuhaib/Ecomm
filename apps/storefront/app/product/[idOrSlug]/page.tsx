import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getTranslations } from 'next-intl/server';
import { getProduct } from '@/app/lib/api';
import VegMarker from '@/app/components/VegMarker';
import PriceTag from '@/app/components/PriceTag';
import AddToCartButton from '@/app/components/AddToCartButton';
import ProductThumb from '@/app/components/ProductThumb';

export const revalidate = 60;

interface Params {
  params: { idOrSlug: string };
}

export async function generateMetadata({ params }: Params) {
  const t = await getTranslations('metadata');
  const product = await getProduct(params.idOrSlug).catch(() => null);
  if (!product) return { title: t('productFallback') };
  return {
    title: t('productTitle', { name: product.name }),
    description: product.description,
  };
}

export default async function ProductPage({ params }: Params) {
  const product = await getProduct(params.idOrSlug);
  if (!product) notFound();

  const t = await getTranslations('product');
  const tc = await getTranslations('common');

  return (
    <>
      <nav className="breadcrumb">
        <Link href="/">{tc('home')}</Link> / <span>{product.name}</span>
      </nav>

      <article className="product-detail">
        <div className="hero-img">
          <ProductThumb product={product} />
        </div>

        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
            <VegMarker veg={product.vegMarker} />
            {product.name}
          </h1>
          {!product.available && (
            <p className="notice error">{t('unavailableNotice')}</p>
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
                  <AddToCartButton variant={v} productName={product.name} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="empty-state">{t('noSizes')}</p>
          )}
        </div>
      </article>
    </>
  );
}
